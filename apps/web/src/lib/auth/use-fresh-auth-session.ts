"use client";

import { useCallback } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getCurrentUser } from "@/lib/api";
import type { UserSummary } from "@/lib/api";
import { clearProtectedDataQueries } from "@/lib/auth-session-cache";
import { queryKeys } from "@/lib/query-keys";
import { isAuthError } from "@/lib/request-errors";

type FreshAuthSessionResult =
  | { status: "verifying" }
  | { status: "authenticated"; user: UserSummary }
  | { status: "unauthenticated" }
  | { status: "error"; error: unknown };

type FreshAuthSession = FreshAuthSessionResult & { retry: () => void };

export const useFreshAuthSession = (): FreshAuthSession => {
  const queryClient = useQueryClient();
  const sessionQuery = useQuery({
    queryFn: async () => {
      const previousUser =
        queryClient.getQueryData<UserSummary>(queryKeys.authMe);
      const currentUser = await getCurrentUser();

      if (previousUser?.id !== currentUser.id) {
        clearProtectedDataQueries(queryClient);
      }

      return currentUser;
    },
    queryKey: queryKeys.authMe,
    refetchOnMount: "always",
    retry: false,
    staleTime: 0,
  });

  const retry = useCallback(() => {
    void sessionQuery.refetch();
  }, [sessionQuery]);

  let result: FreshAuthSessionResult = { status: "verifying" };

  if (sessionQuery.isFetchedAfterMount && !sessionQuery.isFetching) {
    if (sessionQuery.error) {
      result = isAuthError(sessionQuery.error)
        ? { status: "unauthenticated" }
        : { status: "error", error: sessionQuery.error };
    } else if (sessionQuery.data) {
      result = { status: "authenticated", user: sessionQuery.data };
    }
  }

  return { ...result, retry };
};
