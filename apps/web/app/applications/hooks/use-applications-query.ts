"use client";

import { useQuery } from "@tanstack/react-query";
import { listApplications } from "@/lib/api";
import type { ApplicationsListParams } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";

export const useApplicationsQuery = (params: ApplicationsListParams) => {
  return useQuery({
    queryFn: () => listApplications(params),
    queryKey: queryKeys.applications.list(params),
    retry: false,
  });
};
