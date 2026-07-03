import type { QueryClient } from "@tanstack/react-query";
import { queryKeys } from "./query-keys";

export const clearProtectedDataQueries = (queryClient: QueryClient): void => {
  queryClient.removeQueries({ queryKey: queryKeys.applications.all });
  queryClient.removeQueries({ queryKey: queryKeys.dashboardSummary });
  queryClient.removeQueries({ queryKey: queryKeys.settings });
};

export const clearAuthSessionQueries = (queryClient: QueryClient): void => {
  queryClient.removeQueries({ queryKey: queryKeys.authMe });
  clearProtectedDataQueries(queryClient);
};
