/** @jest-environment jsdom */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { ApiError, getCurrentUser, getSettings } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
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

jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"),
  getCurrentUser: jest.fn(),
  getSettings: jest.fn(),
}));

const mockedGetCurrentUser = jest.mocked(getCurrentUser);
const mockedGetSettings = jest.mocked(getSettings);

describe("SettingsPage auth gate", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("redirects unauthenticated users without rendering or loading settings", async () => {
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    queryClient.setQueryData(queryKeys.authMe, {
      id: "stale-user",
      email: "stale@example.com",
      name: null,
    });
    queryClient.setQueryData(queryKeys.settings, { targetRole: "Stale Role" });

    render(
      <QueryClientProvider client={queryClient}>
        <SettingsPage />
      </QueryClientProvider>,
    );

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login"));
    expect(mockedGetSettings).not.toHaveBeenCalled();
    expect(screen.queryByText("Stale Role")).toBeNull();
    expect(queryClient.getQueryData(queryKeys.authMe)).toBeUndefined();
    expect(queryClient.getQueryData(queryKeys.settings)).toBeUndefined();
  });
});
