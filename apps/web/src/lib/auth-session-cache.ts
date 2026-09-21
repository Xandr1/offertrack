import type { QueryClient } from "@tanstack/react-query";
import { queryKeys } from "./query-keys";

export const clearProtectedDataQueries = (queryClient: QueryClient): void => {
  void queryClient.cancelQueries({ queryKey: queryKeys.applications.all });
  void queryClient.cancelQueries({ queryKey: queryKeys.dashboardSummary });
  void queryClient.cancelQueries({ queryKey: queryKeys.settings });
  queryClient.removeQueries({ queryKey: queryKeys.applications.all });
  queryClient.removeQueries({ queryKey: queryKeys.dashboardSummary });
  queryClient.removeQueries({ queryKey: queryKeys.settings });
};

export const clearAuthSessionQueries = (queryClient: QueryClient): void => {
  void queryClient.cancelQueries({ queryKey: queryKeys.authMe });
  const authQuery = queryClient.getQueryCache().find({
    queryKey: queryKeys.authMe,
  });
  authQuery?.setData(undefined as never);
  clearProtectedDataQueries(queryClient);
};
