"use client";

import { useQuery } from "@tanstack/react-query";
import { listApplicationInterviews } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";

export const useApplicationInterviewsQuery = (
  applicationId: string | null,
  enabled: boolean,
) => {
  return useQuery({
    enabled: enabled && applicationId !== null,
    queryFn: () => listApplicationInterviews(applicationId as string),
    queryKey: applicationId
      ? queryKeys.applications.interviews(applicationId)
      : (["applications", "__draft__", "interviews"] as const),
    retry: false,
  });
};
