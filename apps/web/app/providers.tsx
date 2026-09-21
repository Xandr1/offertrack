"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Fragment, ReactNode, useEffect, useState } from "react";
import { authState, subscribeAuthEvents } from "@/lib/auth-coordinator";
import { clearAuthSessionQueries } from "@/lib/auth-session-cache";
import { ApiError } from "@/lib/api/errors";

type ProvidersProps = {
  children: ReactNode;
};

export function Providers({ children }: ProvidersProps) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 5 * 60 * 1000,
            refetchOnMount: true,
            refetchOnWindowFocus: true,
            refetchOnReconnect: true,
            retry: (count, error) => !authState().signedOut &&
              !(error instanceof ApiError && (error.status === 401 || error.status === 403)) && count < 2,
          },
        },
      }),
  );

  const [authGeneration, setAuthGeneration] = useState(0);
  useEffect(() => subscribeAuthEvents(event => {
    if (event === "refreshed") return;
    clearAuthSessionQueries(queryClient);
    setAuthGeneration(value => value + 1);
  }), [queryClient]);

  return (
    <QueryClientProvider client={queryClient}>
      <Fragment key={authGeneration}>{children}</Fragment>
    </QueryClientProvider>
  );
}
