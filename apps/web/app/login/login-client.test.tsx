/** @jest-environment jsdom */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { ApiError, getCurrentUser } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { LoginClient } from "./login-client";

const mockPush = jest.fn();
const mockReplace = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  useSearchParams: () => new URLSearchParams(),
}));

jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"),
  getCurrentUser: jest.fn(),
}));

const mockedGetCurrentUser = jest.mocked(getCurrentUser);

describe("LoginClient session verification", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("does not redirect from stale auth after a fresh unauthorized response", async () => {
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    queryClient.setQueryData(queryKeys.authMe, {
      id: "stale-user",
      email: "stale@example.com",
      name: null,
    });
    queryClient.setQueryData(queryKeys.dashboardSummary, { stale: true });

    render(
      <QueryClientProvider client={queryClient}>
        <LoginClient />
      </QueryClientProvider>,
    );

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeTruthy();
    await waitFor(() => {
      expect(queryClient.getQueryData(queryKeys.authMe)).toBeUndefined();
    });
    expect(queryClient.getQueryData(queryKeys.dashboardSummary)).toBeUndefined();
    expect(mockReplace).not.toHaveBeenCalledWith("/dashboard");
    expect(mockPush).not.toHaveBeenCalledWith("/dashboard");
  });
});
