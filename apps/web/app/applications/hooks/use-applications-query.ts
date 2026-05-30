"use client";

import { useQuery } from "@tanstack/react-query";
import { listApplications } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";

export const useApplicationsQuery = () => {
  return useQuery({
    queryFn: listApplications,
    queryKey: queryKeys.applications.list(),
    retry: false,
  });
};
