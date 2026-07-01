import { QueryClient } from "@tanstack/react-query";
import type { Application, ApplicationInterview } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import {
  invalidateAfterApplicationChange,
  invalidateAfterBoardStageChange,
  invalidateAfterDashboardFollowUp,
  invalidateAfterInterviewChange,
  invalidateApplicationsFeatureQueries,
  setSavedApplicationWithInterviews,
} from "./applications-cache-service";

const application: Application = {
  appliedAt: null,
  companyName: "Acme",
  createdAt: "2026-01-01T00:00:00.000Z",
  id: "app-1",
  jobUrl: null,
  location: null,
  followedUpAt: null,
  lastInterview: null,
  nextInterview: null,
  notes: null,
  positionTitle: "Engineer",
  stage: "interviewing",
  updatedAt: "2026-01-02T00:00:00.000Z",
  workMode: null,
};

const interviews: ApplicationInterview[] = [
  {
    applicationId: "app-1",
    createdAt: "2026-01-02T00:00:00.000Z",
    followedUpAt: null,
    id: "int-1",
    scheduledAt: "2026-01-10T10:00:00.000Z",
    status: "scheduled",
    type: "technical",
    updatedAt: "2026-01-02T00:00:00.000Z",
  },
];

describe("applications cache service", () => {
  it("writes saved application detail and interviews before invalidating feature queries", () => {
    const queryClient = new QueryClient();
    const invalidateQueries = jest.spyOn(queryClient, "invalidateQueries");

    setSavedApplicationWithInterviews(queryClient, {
      application,
      applicationId: application.id,
      interviews,
    });

    expect(
      queryClient.getQueryData(queryKeys.applications.detail(application.id)),
    ).toEqual(application);
    expect(
      queryClient.getQueryData(
        queryKeys.applications.interviews(application.id),
      ),
    ).toEqual(interviews);
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.list(),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.board(),
      refetchType: "none",
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.dashboardSummary,
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.detail(application.id),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.interviews(application.id),
      refetchType: "none",
    });
    expect(invalidateQueries).not.toHaveBeenCalledWith({
      queryKey: queryKeys.applications.interviews(application.id),
      refetchType: "active",
    });
  });

  it("preserves application feature invalidation behavior after application changes", () => {
    const queryClient = new QueryClient();
    const invalidateQueries = jest.spyOn(queryClient, "invalidateQueries");

    invalidateAfterApplicationChange(queryClient, {
      applicationId: application.id,
    });

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.list(),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.board(),
      refetchType: "none",
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.dashboardSummary,
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.detail(application.id),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.interviews(application.id),
      refetchType: "active",
    });
  });

  it("preserves application feature invalidation behavior after interview changes", () => {
    const queryClient = new QueryClient();
    const invalidateQueries = jest.spyOn(queryClient, "invalidateQueries");

    invalidateAfterInterviewChange(queryClient, {
      applicationId: application.id,
    });

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.list(),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.board(),
      refetchType: "none",
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.dashboardSummary,
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.detail(application.id),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.interviews(application.id),
      refetchType: "active",
    });
  });

  it("keeps board stage invalidation scoped to the previous board behavior", () => {
    const queryClient = new QueryClient();
    const invalidateQueries = jest.spyOn(queryClient, "invalidateQueries");

    invalidateAfterBoardStageChange(queryClient, application.id);

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.board(),
      refetchType: "none",
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.list(),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.dashboardSummary,
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.detail(application.id),
    });
    expect(invalidateQueries).not.toHaveBeenCalledWith({
      queryKey: queryKeys.applications.interviews(application.id),
      refetchType: "active",
    });
  });

  it("allows explicit interview refetch suppression after cache writes", () => {
    const queryClient = new QueryClient();
    const invalidateQueries = jest.spyOn(queryClient, "invalidateQueries");

    invalidateApplicationsFeatureQueries(queryClient, {
      applicationId: application.id,
      refetchInterviews: false,
    });

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.interviews(application.id),
      refetchType: "none",
    });
    expect(invalidateQueries).not.toHaveBeenCalledWith({
      queryKey: queryKeys.applications.interviews(application.id),
      refetchType: "active",
    });
  });

  it("preserves dashboard follow-up invalidation behavior", () => {
    const queryClient = new QueryClient();
    const invalidateQueries = jest.spyOn(queryClient, "invalidateQueries");

    invalidateAfterDashboardFollowUp(queryClient, {
      applicationId: application.id,
      kind: "application",
    });

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.list(),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.board(),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.detail(application.id),
    });
    expect(invalidateQueries).not.toHaveBeenCalledWith({
      queryKey: queryKeys.applications.board(),
      refetchType: "none",
    });

    invalidateQueries.mockClear();

    invalidateAfterDashboardFollowUp(queryClient, {
      applicationId: application.id,
      kind: "interview",
    });

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.list(),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.board(),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.interviews(application.id),
    });
  });
});
