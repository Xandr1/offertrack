/** @jest-environment jsdom */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { ApiError, getCurrentUser } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { ProtectedAppBoundary } from "./protected-app-boundary";

let mockPathname = "/dashboard";
const mockReplace = jest.fn();

jest.mock("next/navigation", () => ({
  usePathname: () => mockPathname,
  useRouter: () => ({ replace: mockReplace }),
}));

jest.mock("@/components/layout/shell-layout", () => ({
  ShellLayout: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="protected-page-shell">{children}</div>
  ),
}));

jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"),
  getCurrentUser: jest.fn(),
}));

const mockedGetCurrentUser = jest.mocked(getCurrentUser);
const user = { id: "user-1", email: "person@example.com", name: null };

describe("ProtectedAppBoundary", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPathname = "/dashboard";
  });

  it("keeps one protected boundary mounted across protected navigation", async () => {
    mockedGetCurrentUser.mockResolvedValue(user);
    const queryClient = createQueryClient();
    queryClient.setQueryData(queryKeys.authMe, user);
    queryClient.setQueryData(queryKeys.dashboardSummary, {
      activeProcesses: 2,
    });
    queryClient.setQueryData(queryKeys.applications.list(), {
      items: [{ id: "application-1" }],
    });
    const view = renderBoundary(queryClient, "Dashboard content");

    expect(await screen.findByText("Dashboard content")).toBeTruthy();
    expect(mockedGetCurrentUser).toHaveBeenCalledTimes(1);

    mockPathname = "/applications";
    view.rerenderBoundary("Applications content");

    expect(screen.getByText("Applications content")).toBeTruthy();
    expect(screen.queryByText("Loading applications...")).toBeNull();
    expect(mockedGetCurrentUser).toHaveBeenCalledTimes(1);
    expect(queryClient.getQueryData(queryKeys.dashboardSummary)).toBeDefined();
    expect(
      queryClient.getQueryData(queryKeys.applications.list()),
    ).toBeDefined();
    expect(screen.getByTestId("protected-page-shell")).toBeTruthy();
  });

  it("does not render the protected shell until direct-load auth resolves", async () => {
    let resolveSession: (value: typeof user) => void = () => undefined;
    mockedGetCurrentUser.mockReturnValue(
      new Promise((resolve) => {
        resolveSession = resolve;
      }),
    );
    const queryClient = createQueryClient();

    renderBoundary(queryClient, "Dashboard content");

    expect(screen.getByText("Loading dashboard...")).toBeTruthy();
    expect(screen.queryByTestId("protected-page-shell")).toBeNull();
    expect(screen.queryByText("Dashboard content")).toBeNull();

    resolveSession(user);

    expect(await screen.findByTestId("protected-page-shell")).toBeTruthy();
    expect(screen.getByText("Dashboard content")).toBeTruthy();
  });

  it("never renders the protected shell for an unauthenticated direct load", async () => {
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));
    const queryClient = createQueryClient();
    queryClient.setQueryData(queryKeys.authMe, user);
    queryClient.setQueryData(queryKeys.settings, {
      targetRole: "Cached private role",
    });

    renderBoundary(queryClient, "Dashboard content");

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login"));
    expect(screen.queryByTestId("protected-page-shell")).toBeNull();
    expect(screen.queryByText("Dashboard content")).toBeNull();
    expect(queryClient.getQueryData(queryKeys.authMe)).toBeUndefined();
    expect(queryClient.getQueryData(queryKeys.settings)).toBeUndefined();
  });

  it("leaves public routes outside the protected shell and auth query", () => {
    mockPathname = "/login";
    const queryClient = createQueryClient();

    renderBoundary(queryClient, "Public content");

    expect(screen.getByText("Public content")).toBeTruthy();
    expect(screen.queryByTestId("protected-page-shell")).toBeNull();
    expect(mockedGetCurrentUser).not.toHaveBeenCalled();
  });
});

const createQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

const renderBoundary = (queryClient: QueryClient, content: string) => {
  const makeUi = (nextContent: string) => (
    <QueryClientProvider client={queryClient}>
      <ProtectedAppBoundary>
        <div>{nextContent}</div>
      </ProtectedAppBoundary>
    </QueryClientProvider>
  );
  const view = render(makeUi(content));

  return {
    ...view,
    rerenderBoundary: (nextContent: string) => view.rerender(makeUi(nextContent)),
  };
};
