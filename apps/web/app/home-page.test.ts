/** @jest-environment jsdom */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { getCurrentUser } from "@/lib/api";
import { HomeClient } from "./home-client";
import { LANDING_HEADLINE } from "./landing-content";

const mockReplace = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: mockReplace,
  }),
}));

jest.mock("@/lib/api", () => ({
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
});

const renderWithQueryClient = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    React.createElement(QueryClientProvider, { client: queryClient }, ui),
  );
};
