"use client";

import { useCallback } from "react";
import { useQuery } from "@tanstack/react-query";
import { getCurrentUser } from "@/lib/api";
import type { UserSummary } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { isAuthError } from "@/lib/request-errors";

type FreshAuthSessionResult =
  | { status: "verifying" }
  | { status: "authenticated"; user: UserSummary }
  | { status: "unauthenticated" }
  | { status: "error"; error: unknown };

type FreshAuthSession = FreshAuthSessionResult & { retry: () => void };

export const useFreshAuthSession = (): FreshAuthSession => {
  const sessionQuery = useQuery({
    queryFn: getCurrentUser,
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
