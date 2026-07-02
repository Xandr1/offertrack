/** @jest-environment jsdom */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { ApiError, getCurrentUser } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { HomeClient } from "./home-client";
import { LANDING_HEADLINE } from "./landing-content";

const mockReplace = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: mockReplace,
  }),
}));

jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"),
  getCurrentUser: jest.fn(),
}));

const mockedGetCurrentUser = getCurrentUser as jest.MockedFunction<
  typeof getCurrentUser
>;

describe("HomeClient", () => {
  afterEach(() => {
    mockReplace.mockReset();
    mockedGetCurrentUser.mockReset();
  });

  it("redirects authenticated users to the dashboard", async () => {
    mockedGetCurrentUser.mockResolvedValueOnce({
      email: "person@example.com",
      id: "user-1",
      name: null,
    });

    renderWithQueryClient(React.createElement(HomeClient));

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/dashboard");
    });
  });

  it("renders the landing page when the session check fails", async () => {
    mockedGetCurrentUser.mockRejectedValueOnce(new Error("Unauthorized"));

    renderWithQueryClient(React.createElement(HomeClient));

    expect(
      await screen.findByRole("heading", { name: LANDING_HEADLINE }),
    ).toBeTruthy();
    expect(screen.queryByText("Unauthorized")).toBeNull();
  });

  it("renders the landing page while the session check is pending", () => {
    mockedGetCurrentUser.mockReturnValueOnce(
      new Promise<Awaited<ReturnType<typeof getCurrentUser>>>(() => undefined),
    );

    renderWithQueryClient(React.createElement(HomeClient));

    expect(screen.getByRole("heading", { name: LANDING_HEADLINE })).toBeTruthy();
  });

  it("ignores and clears stale auth when the fresh check is unauthorized", async () => {
    mockedGetCurrentUser.mockRejectedValueOnce(new ApiError(401, "Unauthorized"));
    const queryClient = renderWithQueryClient(
      React.createElement(HomeClient),
      true,
    );

    expect(
      await screen.findByRole("heading", { name: LANDING_HEADLINE }),
    ).toBeTruthy();
    await waitFor(() => {
      expect(queryClient.getQueryData(queryKeys.authMe)).toBeUndefined();
    });
    expect(mockReplace).not.toHaveBeenCalledWith("/dashboard");
  });
});

const renderWithQueryClient = (ui: React.ReactElement, seedStaleAuth = false) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  if (seedStaleAuth) {
    queryClient.setQueryData(queryKeys.authMe, {
      id: "stale-user",
      email: "stale@example.com",
      name: null,
    });
  }

  render(
    React.createElement(QueryClientProvider, { client: queryClient }, ui),
  );

  return queryClient;
};
