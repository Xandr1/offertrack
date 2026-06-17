import { QueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query-keys";

type InvalidateApplicationsFeatureQueriesInput = {
  applicationId?: string | null;
  refetchInterviews?: boolean;
};

export const invalidateApplicationsFeatureQueries = (
  queryClient: QueryClient,
  input: InvalidateApplicationsFeatureQueriesInput = {},
) => {
  const shouldRefetchInterviews = input.refetchInterviews ?? true;

  void queryClient.invalidateQueries({ queryKey: queryKeys.applications.list() });
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
