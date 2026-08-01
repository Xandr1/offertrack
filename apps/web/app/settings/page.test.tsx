/** @jest-environment jsdom */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  ApiError,
  getCurrentUser,
  getSettings,
  logout,
  updateSettings,
} from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { ProtectedAppBoundary } from "../protected-app-boundary";
import SettingsPage from "./page";

const mockReplace = jest.fn();

jest.mock("next/navigation", () => ({
  usePathname: () => "/settings",
  useRouter: () => ({ replace: mockReplace }),
}));

jest.mock("next/link", () => ({
  __esModule: true,
  default: "a",
}));

jest.mock("@/components/layout/shell-layout", () => ({
  ShellLayout: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"),
  getCurrentUser: jest.fn(),
  getSettings: jest.fn(),
  logout: jest.fn(),
  updateSettings: jest.fn(),
}));

const mockedGetCurrentUser = jest.mocked(getCurrentUser);
const mockedGetSettings = jest.mocked(getSettings);
const mockedLogout = jest.mocked(logout);
const mockedUpdateSettings = jest.mocked(updateSettings);

const settings = {
  followUpAfterApplyingDays: 10,
  upcomingInterviewDays: 14,
  followUpAfterInterviewDays: 4,
  targetRole: "Platform Engineer",
};

describe("SettingsPage auth gate", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedGetCurrentUser.mockResolvedValue({
      id: "user-1",
      email: "person@example.com",
      name: "Person",
    });
    mockedGetSettings.mockResolvedValue(settings);
    mockedLogout.mockResolvedValue();
  });

  it("redirects unauthenticated users without rendering or loading settings", async () => {
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));
    const queryClient = createQueryClient();
    queryClient.setQueryData(queryKeys.authMe, {
      id: "stale-user",
      email: "stale@example.com",
      name: null,
    });
    queryClient.setQueryData(queryKeys.settings, { targetRole: "Stale Role" });

    render(
      <QueryClientProvider client={queryClient}>
        <ProtectedAppBoundary>
          <SettingsPage />
        </ProtectedAppBoundary>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login"));
    expect(mockedGetSettings).not.toHaveBeenCalled();
    expect(screen.queryByText("Stale Role")).toBeNull();
    expect(queryClient.getQueryData(queryKeys.authMe)).toBeUndefined();
    expect(queryClient.getQueryData(queryKeys.settings)).toBeUndefined();
  });

  it("redirects and clears session caches when the initial settings query is unauthorized", async () => {
    let rejectSettings: (error: unknown) => void = () => undefined;
    mockedGetSettings.mockReturnValue(
      new Promise((_resolve, reject) => {
        rejectSettings = reject;
      }),
    );
    const queryClient = createQueryClient();

    renderPage(queryClient);
    await waitFor(() => expect(mockedGetSettings).toHaveBeenCalled());
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));
    seedProtectedCaches(queryClient);
    rejectSettings(new ApiError(401, "Unauthorized"));

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login"));
    expectProtectedCachesCleared(queryClient);
    expect(screen.queryByText("Settings unavailable")).toBeNull();
  });

  it("redirects for forbidden settings when session verification fails", async () => {
    let rejectSettings: (error: unknown) => void = () => undefined;
    mockedGetSettings.mockReturnValue(
      new Promise((_resolve, reject) => {
        rejectSettings = reject;
      }),
    );
    const queryClient = createQueryClient();

    renderPage(queryClient);
    await waitFor(() => expect(mockedGetSettings).toHaveBeenCalled());
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));
    seedProtectedCaches(queryClient);
    rejectSettings(new ApiError(403, "Forbidden"));

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login"));
    expectProtectedCachesCleared(queryClient);
    expect(screen.queryByText("Settings unavailable")).toBeNull();
  });

  it("shows retryable forbidden settings when session verification succeeds", async () => {
    let rejectSettings: (error: unknown) => void = () => undefined;
    mockedGetSettings.mockReturnValue(
      new Promise((_resolve, reject) => {
        rejectSettings = reject;
      }),
    );
    const queryClient = createQueryClient();

    renderPage(queryClient);
    await waitFor(() => expect(mockedGetSettings).toHaveBeenCalled());
    seedProtectedCaches(queryClient);
    rejectSettings(new ApiError(403, "Forbidden"));

    expect(await screen.findByText("Settings unavailable")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
    await waitFor(() => expect(mockedGetCurrentUser).toHaveBeenCalledTimes(2));
    expect(mockReplace).not.toHaveBeenCalledWith("/login");
    expectProtectedCachesRetained(queryClient);
  });

  it("keeps the retryable settings error for non-auth failures", async () => {
    mockedGetSettings.mockRejectedValue(new Error("network failed"));

    renderPage(createQueryClient());

    expect(await screen.findByText("Settings unavailable")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
    expect(mockReplace).not.toHaveBeenCalledWith("/login");
  });

  it("keeps settings mutation auth cleanup and redirect behavior", async () => {
    mockedUpdateSettings.mockRejectedValue(new ApiError(401, "Unauthorized"));
    const queryClient = createQueryClient();
    const user = userEvent.setup();

    renderPage(queryClient);
    const saveButton = await screen.findByRole("button", {
      name: "Save changes",
    });
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));
    seedProtectedCaches(queryClient);
    await user.click(saveButton);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login"));
    expectProtectedCachesCleared(queryClient);
    expect(
      screen.queryByText("Something went wrong. Please try again."),
    ).toBeNull();
  });

  it("clears protected data when the user signs out", async () => {
    const queryClient = createQueryClient();
    const user = userEvent.setup();

    renderPage(queryClient);
    await screen.findByRole("heading", { name: "Settings" });
    queryClient.setQueryData(queryKeys.dashboardSummary, { stale: true });
    queryClient.setQueryData(queryKeys.applications.list(), { stale: true });

    await user.click(screen.getByRole("button", { name: "Sign out" }));

    await waitFor(() => expect(mockedLogout).toHaveBeenCalledTimes(1));
    expect(mockReplace).toHaveBeenCalledWith("/login");
    expect(queryClient.getQueryData(queryKeys.authMe)).toBeUndefined();
    expect(queryClient.getQueryData(queryKeys.settings)).toBeUndefined();
    expect(queryClient.getQueryData(queryKeys.dashboardSummary)).toBeUndefined();
    expect(
      queryClient.getQueryData(queryKeys.applications.list()),
    ).toBeUndefined();
  });
});

const createQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

const renderPage = (queryClient: QueryClient) =>
  render(
    <QueryClientProvider client={queryClient}>
      <ProtectedAppBoundary>
        <SettingsPage />
      </ProtectedAppBoundary>
    </QueryClientProvider>,
  );

const seedProtectedCaches = (queryClient: QueryClient) => {
  queryClient.setQueryData(queryKeys.authMe, {
    id: "user-1",
    email: "person@example.com",
    name: "Person",
  });
  queryClient.setQueryData(queryKeys.settings, settings);
  queryClient.setQueryData(queryKeys.dashboardSummary, { stale: true });
  queryClient.setQueryData(queryKeys.applications.list(), { stale: true });
};

const expectProtectedCachesCleared = (queryClient: QueryClient) => {
  expect(queryClient.getQueryData(queryKeys.authMe)).toBeUndefined();
  expect(queryClient.getQueryData(queryKeys.settings)).toBeUndefined();
  expect(queryClient.getQueryData(queryKeys.dashboardSummary)).toBeUndefined();
  expect(queryClient.getQueryData(queryKeys.applications.list())).toBeUndefined();
};

const expectProtectedCachesRetained = (queryClient: QueryClient) => {
  expect(queryClient.getQueryData(queryKeys.authMe)).toBeDefined();
  expect(queryClient.getQueryData(queryKeys.settings)).toBeDefined();
  expect(queryClient.getQueryData(queryKeys.dashboardSummary)).toBeDefined();
  expect(queryClient.getQueryData(queryKeys.applications.list())).toBeDefined();
};
