"use client";

import { useCallback, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getCurrentUser } from "@/lib/api";
import type { UserSummary } from "@/lib/api";
import { clearProtectedDataQueries } from "@/lib/auth-session-cache";
import { queryKeys } from "@/lib/query-keys";
import { isAuthError } from "@/lib/request-errors";

type FreshAuthSessionResult =
  | { status: "verifying" }
  | {
      status: "authenticated";
      user: UserSummary;
      isRefreshing: boolean;
    }
  | { status: "unauthenticated" }
  | { status: "error"; error: unknown };

type FreshAuthSession = FreshAuthSessionResult & { retry: () => void };

export const useFreshAuthSession = (): FreshAuthSession => {
  const queryClient = useQueryClient();
  const [initialDataUpdateCount] = useState(
    () => queryClient.getQueryState(queryKeys.authMe)?.dataUpdateCount ?? 0,
  );

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
  const currentDataUpdateCount =
    queryClient.getQueryState(queryKeys.authMe)?.dataUpdateCount ?? 0;
  // Track fetch status eagerly so a verification that returns structurally
  // shared cached data still produces the render that marks it as verified.
  const isFetching = sessionQuery.isFetching;
  const hasVerifiedUser =
    Boolean(sessionQuery.data) &&
    currentDataUpdateCount > initialDataUpdateCount;
  const hasCompletedError =
    Boolean(sessionQuery.error) && !isFetching;

  if (hasCompletedError && isAuthError(sessionQuery.error)) {
    result = { status: "unauthenticated" };
  } else if (hasVerifiedUser && sessionQuery.data) {
    result = {
      status: "authenticated",
      user: sessionQuery.data,
      isRefreshing: isFetching,
    };
  } else if (hasCompletedError) {
    result = { status: "error", error: sessionQuery.error };
  }

  return { ...result, retry };
};
