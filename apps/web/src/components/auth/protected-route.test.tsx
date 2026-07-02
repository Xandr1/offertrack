/** @jest-environment jsdom */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ApiError, getCurrentUser } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { ProtectedRoute } from "./protected-route";

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

  it("does not trust cached auth and clears stale protected data before rendering", async () => {
    let resolveSession: (value: typeof user) => void = () => undefined;
    mockedGetCurrentUser.mockReturnValue(
      new Promise((resolve) => {
        resolveSession = resolve;
      }),
    );
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    queryClient.setQueryData(queryKeys.authMe, {
      ...user,
      email: "stale@example.com",
    });
    queryClient.setQueryData(queryKeys.applications.list(), {
      items: [{ companyName: "Stale Company" }],
    });

    renderGate(queryClient);

    expect(screen.getByText("Checking protected route...")).toBeTruthy();
    expect(screen.queryByText("Protected content")).toBeNull();
    expect(mockedGetCurrentUser).toHaveBeenCalledTimes(1);

    resolveSession(user);

    expect(await screen.findByText("Protected content")).toBeTruthy();
    expect(queryClient.getQueryData(queryKeys.applications.list())).toBeUndefined();
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
});

const renderGate = (queryClient: QueryClient) =>
  render(
    <QueryClientProvider client={queryClient}>
      <ProtectedRoute
        errorTitle="Protected unavailable"
        loadingLabel="Checking protected route..."
      >
        {() => <div>Protected content</div>}
      </ProtectedRoute>
    </QueryClientProvider>,
  );
