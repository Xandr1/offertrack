import { QueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query-keys";

export const invalidateSettingsFeatureQueries = (queryClient: QueryClient) => {
  void queryClient.invalidateQueries({ queryKey: queryKeys.settings });
  void queryClient.invalidateQueries({ queryKey: queryKeys.dashboardSummary });
};
