import { QueryClient } from "@tanstack/react-query";
import type { Application, ApplicationInterview } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";

type InvalidateApplicationsFeatureQueriesInput = {
  applicationId?: string | null;
  refetchInterviews?: boolean;
};

type SavedApplicationWithInterviews = {
  application: Application;
  applicationId: string;
  interviews: ApplicationInterview[];
};

type DashboardFollowUpInput = {
  applicationId: string;
  kind: "application" | "interview";
};

export const setApplicationDetail = (
  queryClient: QueryClient,
  application: Application,
) => {
  queryClient.setQueryData(
    queryKeys.applications.detail(application.id),
    application,
  );
};

export const setApplicationInterviews = (
  queryClient: QueryClient,
  applicationId: string,
  interviews: ApplicationInterview[],
) => {
  queryClient.setQueryData(
    queryKeys.applications.interviews(applicationId),
    interviews,
  );
};

export const invalidateApplicationsFeatureQueries = (
  queryClient: QueryClient,
  input: InvalidateApplicationsFeatureQueriesInput = {},
) => {
  const shouldRefetchInterviews = input.refetchInterviews ?? true;

  void queryClient.invalidateQueries({ queryKey: queryKeys.applications.list() });
  void queryClient.invalidateQueries({
    queryKey: queryKeys.applications.board(),
    refetchType: "none",
  });
  void queryClient.invalidateQueries({ queryKey: queryKeys.dashboardSummary });

  if (!input.applicationId) {
    return;
  }

  void queryClient.invalidateQueries({
    queryKey: queryKeys.applications.detail(input.applicationId),
  });
  void queryClient.invalidateQueries({
    queryKey: queryKeys.applications.interviews(input.applicationId),
    refetchType: shouldRefetchInterviews ? "active" : "none",
  });
};

export const setSavedApplicationWithInterviews = (
  queryClient: QueryClient,
  result: SavedApplicationWithInterviews,
) => {
  queryClient.setQueryData(
    queryKeys.applications.detail(result.applicationId),
    result.application,
  );
  setApplicationInterviews(
    queryClient,
    result.applicationId,
    result.interviews,
  );
  invalidateApplicationsFeatureQueries(queryClient, {
    applicationId: result.applicationId,
    refetchInterviews: false,
  });
};

export const invalidateAfterApplicationChange = (
  queryClient: QueryClient,
  input: InvalidateApplicationsFeatureQueriesInput = {},
) => {
  invalidateApplicationsFeatureQueries(queryClient, input);
};

export const invalidateAfterInterviewChange = (
  queryClient: QueryClient,
  input: InvalidateApplicationsFeatureQueriesInput = {},
) => {
  invalidateApplicationsFeatureQueries(queryClient, input);
};

export const invalidateAfterBoardStageChange = (
  queryClient: QueryClient,
  applicationId: string,
) => {
  void queryClient.invalidateQueries({
    queryKey: queryKeys.applications.board(),
    refetchType: "none",
  });
  void queryClient.invalidateQueries({ queryKey: queryKeys.applications.list() });
  void queryClient.invalidateQueries({ queryKey: queryKeys.dashboardSummary });
  void queryClient.invalidateQueries({
    queryKey: queryKeys.applications.detail(applicationId),
  });
};

export const invalidateAfterDashboardFollowUp = (
  queryClient: QueryClient,
  input: DashboardFollowUpInput,
) => {
  void queryClient.invalidateQueries({ queryKey: queryKeys.applications.list() });
  void queryClient.invalidateQueries({ queryKey: queryKeys.applications.board() });

  if (input.kind === "application") {
    void queryClient.invalidateQueries({
      queryKey: queryKeys.applications.detail(input.applicationId),
    });
    return;
  }

  void queryClient.invalidateQueries({
    queryKey: queryKeys.applications.interviews(input.applicationId),
  });
};
