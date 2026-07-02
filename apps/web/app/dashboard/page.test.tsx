/** @jest-environment jsdom */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import {
  ApiError,
  getCurrentUser,
  getDashboardApplicationsToFollowUp,
  getDashboardInterviewsToFollowUp,
  getDashboardSummary,
  getDashboardUpcomingInterviews,
  markApplicationFollowedUp,
  markInterviewFollowedUp,
} from "@/lib/api";
import type { DashboardSummary } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import DashboardPage, { FOLLOW_UP_UNDO_TIMEOUT_MS } from "./page";

const mockReplace = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

jest.mock("@/components/layout/shell-layout", () => ({
  ShellLayout: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"),
  getCurrentUser: jest.fn(),
  getDashboardApplicationsToFollowUp: jest.fn(),
  getDashboardInterviewsToFollowUp: jest.fn(),
  getDashboardSummary: jest.fn(),
  getDashboardUpcomingInterviews: jest.fn(),
  markApplicationFollowedUp: jest.fn(),
  markInterviewFollowedUp: jest.fn(),
}));

const mockedGetCurrentUser = jest.mocked(getCurrentUser);
const mockedGetSummary = jest.mocked(getDashboardSummary);
const mockedLoadApplications = jest.mocked(getDashboardApplicationsToFollowUp);
const mockedLoadUpcoming = jest.mocked(getDashboardUpcomingInterviews);
const mockedLoadInterviews = jest.mocked(getDashboardInterviewsToFollowUp);
const mockedMarkApplication = jest.mocked(markApplicationFollowedUp);
const mockedMarkInterview = jest.mocked(markInterviewFollowedUp);

const applicationItem = {
  applicationId: "app-1",
  companyName: "Acme",
  positionTitle: "Backend Engineer",
  stage: "applied" as const,
  jobUrl: null,
  location: "Remote",
  workMode: "remote" as const,
  appliedAt: "2026-05-20T10:00:00Z",
  createdAt: "2026-05-20T10:00:00Z",
  updatedAt: "2026-05-20T10:00:00Z",
};

const summary = (hasMore = false): DashboardSummary => ({
  activeProcesses: 1,
  needsAttention: 1,
  interviewing: 0,
  offers: 0,
  rejected: 0,
  followUpAfterApplyingDays: 7,
  upcomingInterviewDays: 7,
  followUpAfterInterviewDays: 2,
  applicationsToFollowUp: {
    totalCount: hasMore ? 2 : 1,
    items: [applicationItem],
    nextOffset: 1,
    hasMore,
  },
  upcomingInterviews: { totalCount: 0, items: [], nextOffset: 0, hasMore: false },
  interviewsToFollowUp: { totalCount: 0, items: [], nextOffset: 0, hasMore: false },
});

describe("DashboardPage follow-up actions", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedGetCurrentUser.mockResolvedValue({
      id: "user-1",
      email: "person@example.com",
      name: "Person",
    });
    mockedGetSummary.mockResolvedValue(summary());
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("redirects and clears session caches when the summary query is unauthorized", async () => {
    let rejectSummary: (error: unknown) => void = () => undefined;
    mockedGetSummary.mockReturnValue(
      new Promise((_resolve, reject) => {
        rejectSummary = reject;
      }),
    );
    const view = renderPage();

    await waitFor(() => expect(mockedGetSummary).toHaveBeenCalled());
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));
    seedProtectedCaches(view.queryClient);
    await act(async () => {
      rejectSummary(new ApiError(401, "Unauthorized"));
      await Promise.resolve();
    });

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login"));
    expectProtectedCachesCleared(view.queryClient);
    expect(screen.queryByText("Summary unavailable")).toBeNull();
  });

  it("redirects and clears session caches when a follow-up mutation is unauthorized", async () => {
    mockedMarkApplication.mockRejectedValue(new ApiError(401, "Unauthorized"));
    const view = renderPage();
    const markButton = await screen.findByRole("button", {
      name: "Mark followed up",
    });
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));
    seedProtectedCaches(view.queryClient);
    jest.useFakeTimers();

    fireEvent.click(markButton);
    await act(async () => {
      jest.advanceTimersByTime(FOLLOW_UP_UNDO_TIMEOUT_MS);
      await Promise.resolve();
    });
    jest.useRealTimers();

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login"));
    expectProtectedCachesCleared(view.queryClient);
    expect(
      screen.queryByText("Something went wrong. Please try again."),
    ).toBeNull();
  });

  it("redirects and clears session caches when load more is unauthorized", async () => {
    mockedGetSummary.mockResolvedValue(summary(true));
    mockedLoadApplications.mockRejectedValue(new ApiError(401, "Unauthorized"));
    const view = renderPage();

    const loadMoreButton = await screen.findByRole("button", {
      name: "Load more",
    });
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));
    seedProtectedCaches(view.queryClient);
    fireEvent.click(loadMoreButton);

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login"));
    expectProtectedCachesCleared(view.queryClient);
    expect(
      screen.queryByText("Something went wrong. Please try again."),
    ).toBeNull();
  });

  it("keeps the retryable summary error for non-auth failures", async () => {
    mockedGetSummary.mockRejectedValue(new Error("network failed"));

    renderPage();

    expect(await screen.findByText("Summary unavailable")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Retry" })).toBeTruthy();
    expect(mockReplace).not.toHaveBeenCalledWith("/login");
  });

  it("does not load or render dashboard data before fresh auth succeeds", async () => {
    let resolveSession: (value: {
      id: string;
      email: string;
      name: string;
    }) => void = () => undefined;
    mockedGetCurrentUser.mockReturnValue(
      new Promise((resolve) => {
        resolveSession = resolve;
      }),
    );

    renderPage();

    expect(screen.getByText("Loading dashboard...")).toBeTruthy();
    expect(mockedGetSummary).not.toHaveBeenCalled();
    expect(
      screen.queryByRole("heading", { name: "Applications to follow up" }),
    ).toBeNull();

    resolveSession({
      id: "user-1",
      email: "person@example.com",
      name: "Person",
    });

    expect(
      await screen.findByRole("heading", { name: "Applications to follow up" }),
    ).toBeTruthy();
    expect(mockedGetSummary).toHaveBeenCalledTimes(1);
  });

  it("renders exactly the three action modules", async () => {
    renderPage();

    expect(await screen.findByRole("heading", { name: "Applications to follow up" })).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Upcoming interviews" })).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Interviews to follow up" })).toBeTruthy();
    expect(screen.queryByText("Drafts")).toBeNull();
  });

  it("cancels a pending follow-up without an API call when undone", async () => {
    renderPage();
    const markButton = await screen.findByRole("button", { name: "Mark followed up" });
    jest.useFakeTimers();

    fireEvent.click(markButton);
    expect(screen.getByText("Marked followed up")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Undo" }));
    act(() => jest.advanceTimersByTime(FOLLOW_UP_UNDO_TIMEOUT_MS));

    expect(mockedMarkApplication).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Mark followed up" })).toBeTruthy();
  });

  it("commits once after the delay and removes the successful item", async () => {
    mockedMarkApplication.mockResolvedValue({
      id: "app-1",
      companyName: "Acme",
      positionTitle: "Backend Engineer",
      jobUrl: null,
      location: "Remote",
      workMode: "remote",
      stage: "applied",
      notes: null,
      appliedAt: "2026-05-20T10:00:00Z",
      followedUpAt: "2026-06-06T12:00:00Z",
      createdAt: "2026-05-20T10:00:00Z",
      updatedAt: "2026-06-06T12:00:00Z",
      nextInterview: null,
      lastInterview: null,
    });
    renderPage();
    const markButton = await screen.findByRole("button", { name: "Mark followed up" });
    jest.useFakeTimers();

    fireEvent.click(markButton);
    await act(async () => {
      jest.advanceTimersByTime(FOLLOW_UP_UNDO_TIMEOUT_MS);
      await Promise.resolve();
    });
    jest.useRealTimers();

    await waitFor(() => expect(mockedMarkApplication).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByText("Acme")).toBeNull());
  });

  it("restores the action and shows the module error after a failed commit", async () => {
    mockedMarkApplication.mockRejectedValue(new Error("Follow-up failed"));
    renderPage();
    const markButton = await screen.findByRole("button", { name: "Mark followed up" });
    jest.useFakeTimers();

    fireEvent.click(markButton);
    await act(async () => {
      jest.advanceTimersByTime(FOLLOW_UP_UNDO_TIMEOUT_MS);
      await Promise.resolve();
    });
    jest.useRealTimers();

    expect(await screen.findByText("Something went wrong. Please try again.")).toBeTruthy();
    expect(screen.getByText("Acme")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Mark followed up" })).toBeTruthy();
  });

  it("loads only the requested module and appends its page", async () => {
    mockedGetSummary.mockResolvedValue(summary(true));
    mockedLoadApplications.mockResolvedValue({
      totalCount: 2,
      items: [{ ...applicationItem, applicationId: "app-2", companyName: "Globex" }],
      nextOffset: 2,
      hasMore: false,
    });
    renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Load more" }));

    expect(await screen.findByText("Globex")).toBeTruthy();
    expect(mockedLoadApplications.mock.calls[0]?.[0]).toBe(1);
    expect(mockedLoadUpcoming).not.toHaveBeenCalled();
    expect(mockedLoadInterviews).not.toHaveBeenCalled();
    expect(mockedMarkInterview).not.toHaveBeenCalled();
  });
});

const renderPage = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <DashboardPage />
    </QueryClientProvider>,
  );

  return { ...view, queryClient };
};

const seedProtectedCaches = (queryClient: QueryClient) => {
  queryClient.setQueryData(queryKeys.authMe, {
    id: "stale-user",
    email: "stale@example.com",
    name: null,
  });
  queryClient.setQueryData(queryKeys.dashboardSummary, summary());
  queryClient.setQueryData(queryKeys.settings, { stale: true });
  queryClient.setQueryData(queryKeys.applications.list(), { stale: true });
};

const expectProtectedCachesCleared = (queryClient: QueryClient) => {
  expect(queryClient.getQueryData(queryKeys.authMe)).toBeUndefined();
  expect(queryClient.getQueryData(queryKeys.dashboardSummary)).toBeUndefined();
  expect(queryClient.getQueryData(queryKeys.settings)).toBeUndefined();
  expect(queryClient.getQueryData(queryKeys.applications.list())).toBeUndefined();
};
