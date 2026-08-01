/** @jest-environment jsdom */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { ApiError, getCurrentUser } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { ProtectedRoute, useProtectedUser } from "./protected-route";

const mockReplace = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"),
  getCurrentUser: jest.fn(),
}));

const mockedGetCurrentUser = jest.mocked(getCurrentUser);
const user = { id: "user-1", email: "person@example.com", name: null };

describe("ProtectedRoute", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("does not trust cached auth but retains protected data for the verified user", async () => {
    let resolveSession: (value: typeof user) => void = () => undefined;
    mockedGetCurrentUser.mockReturnValue(
      new Promise((resolve) => {
        resolveSession = resolve;
      }),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    queryClient.setQueryData(queryKeys.authMe, user);
    queryClient.setQueryData(queryKeys.applications.list(), {
      items: [{ companyName: "Cached Company" }],
    });

    renderGate(queryClient);

    expect(screen.getByText("Checking protected route...")).toBeTruthy();
    expect(screen.queryByText("Protected content")).toBeNull();
    expect(mockedGetCurrentUser).toHaveBeenCalledTimes(1);

    resolveSession(user);

    expect(await screen.findByText("Protected content")).toBeTruthy();
    expect(queryClient.getQueryData(queryKeys.applications.list())).toBeDefined();
  });

  it("clears protected data before rendering for a different verified user", async () => {
    const previousUser = {
      id: "user-previous",
      email: "previous@example.com",
      name: null,
    };
    mockedGetCurrentUser.mockResolvedValue(user);
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    queryClient.setQueryData(queryKeys.authMe, previousUser);
    queryClient.setQueryData(queryKeys.applications.list(), {
      items: [{ companyName: "Previous User Company" }],
    });

    renderGate(queryClient);

    expect(screen.getByText("Checking protected route...")).toBeTruthy();
    expect(screen.queryByText("Protected content")).toBeNull();
    expect(await screen.findByText("Protected content")).toBeTruthy();
    expect(queryClient.getQueryData(queryKeys.applications.list())).toBeUndefined();
  });

  it("does not render a direct protected load while authentication is unresolved", async () => {
    let resolveSession: (value: typeof user) => void = () => undefined;
    mockedGetCurrentUser.mockReturnValue(
      new Promise((resolve) => {
        resolveSession = resolve;
      }),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    renderGate(queryClient);

    expect(screen.getByText("Checking protected route...")).toBeTruthy();
    expect(screen.queryByText("Protected content")).toBeNull();

    resolveSession(user);

    expect(await screen.findByText("Protected content")).toBeTruthy();
  });

  it("clears the session and redirects without rendering on an auth error", async () => {
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "oauth-token-marker"));
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    queryClient.setQueryData(queryKeys.authMe, user);
    queryClient.setQueryData(queryKeys.settings, { stale: true });

    renderGate(queryClient);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login"));
    expect(queryClient.getQueryData(queryKeys.authMe)).toBeUndefined();
    expect(queryClient.getQueryData(queryKeys.settings)).toBeUndefined();
    expect(screen.queryByText("Protected content")).toBeNull();
  });

  it("shows a retryable error for non-auth failures", async () => {
    mockedGetCurrentUser
      .mockRejectedValueOnce(new Error("network failed"))
      .mockResolvedValueOnce(user);
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    renderGate(queryClient);

    expect(await screen.findByText("Protected unavailable")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText("Protected content")).toBeTruthy();
    expect(mockedGetCurrentUser).toHaveBeenCalledTimes(2);
  });

  it("clears protected data when the authenticated identity changes", async () => {
    let resolveChangedSession: (value: typeof user) => void = () => undefined;
    mockedGetCurrentUser
      .mockResolvedValueOnce(user)
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveChangedSession = resolve;
        }),
      );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    renderGate(queryClient, <UserScopedContent />);
    expect(await screen.findByText("Owner user-1")).toBeTruthy();
    queryClient.setQueryData(queryKeys.settings, {
      targetRole: "Current User Role",
    });

    act(() => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.authMe });
    });
    await waitFor(() => expect(mockedGetCurrentUser).toHaveBeenCalledTimes(2));
    expect(screen.getByText("Checking protected route...")).toBeTruthy();
    expect(screen.queryByText("Owner user-1")).toBeNull();

    resolveChangedSession({
      id: "user-2",
      email: "other@example.com",
      name: null,
    });

    await waitFor(() => {
      expect(queryClient.getQueryData(queryKeys.settings)).toBeUndefined();
    });
    expect(screen.getByText("Owner user-2")).toBeTruthy();
  });
});

const renderGate = (
  queryClient: QueryClient,
  children: React.ReactNode = <div>Protected content</div>,
) =>
  render(
    <QueryClientProvider client={queryClient}>
      <ProtectedRoute
        errorTitle="Protected unavailable"
        loadingLabel="Checking protected route..."
      >
        {children}
      </ProtectedRoute>
    </QueryClientProvider>,
  );

const UserScopedContent = () => {
  const user = useProtectedUser();
  const [ownerId] = React.useState(user.id);

  return <div>Owner {ownerId}</div>;
};
