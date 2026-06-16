"use client";

import { useQuery } from "@tanstack/react-query";
import { getApplication } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";

export const useApplicationQuery = (
  applicationId: string | null,
  enabled: boolean,
) => {
  return useQuery({
    enabled: enabled && applicationId !== null,
    queryFn: () => getApplication(applicationId as string),
    queryKey: applicationId
      ? queryKeys.applications.detail(applicationId)
      : (["applications", "detail", "__none__"] as const),
    retry: false,
  });
};
