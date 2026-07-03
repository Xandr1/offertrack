"use client";

import { ReactNode, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import type { UserSummary } from "@/lib/api";
import {
  clearAuthSessionQueries,
  clearProtectedDataQueries,
} from "@/lib/auth-session-cache";
import { useFreshAuthSession } from "@/lib/auth/use-fresh-auth-session";
import { getRequestErrorMessage } from "@/lib/request-errors";
import { formStyles, pageStyles, textStyles } from "@/lib/styles";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

type ProtectedRouteProps = {
  children: (user: UserSummary) => ReactNode;
  errorTitle: string;
  loadingLabel: string;
};

export const ProtectedRoute = ({
  children,
  errorTitle,
  loadingLabel,
}: ProtectedRouteProps) => {
  const router = useRouter();
  const queryClient = useQueryClient();
  const session = useFreshAuthSession();

  useEffect(() => {
    if (session.status !== "unauthenticated") {
      return;
    }

    clearAuthSessionQueries(queryClient);
    router.replace("/login");
  }, [queryClient, router, session.status]);

  if (session.status === "verifying") {
    return (
      <main className={pageStyles.centered}>
        <div className={pageStyles.statusMessage}>{loadingLabel}</div>
      </main>
    );
  }

  if (session.status === "unauthenticated") {
    return null;
  }

  if (session.status === "error") {
    return (
      <main className={pageStyles.centered}>
        <Card variant="auth">
          <h1 className={textStyles.pageTitle}>{errorTitle}</h1>
          <div className={`mt-4 ${formStyles.error}`}>
            {getRequestErrorMessage(session.error)}
          </div>
          <Button className="mt-4" onClick={session.retry} variant="secondary">
            Retry
          </Button>
        </Card>
      </main>
    );
  }

  return (
    <ProtectedCacheBoundary
      loadingLabel={loadingLabel}
      queryClient={queryClient}
    >
      {children(session.user)}
    </ProtectedCacheBoundary>
  );
};

type ProtectedCacheBoundaryProps = {
  children: ReactNode;
  loadingLabel: string;
  queryClient: ReturnType<typeof useQueryClient>;
};

const ProtectedCacheBoundary = ({
  children,
  loadingLabel,
  queryClient,
}: ProtectedCacheBoundaryProps) => {
  const [isPrepared, setIsPrepared] = useState(false);

  useEffect(() => {
    let isActive = true;
    clearProtectedDataQueries(queryClient);
    queueMicrotask(() => {
      if (isActive) {
        setIsPrepared(true);
      }
    });

    return () => {
      isActive = false;
    };
  }, [queryClient]);

  if (!isPrepared) {
    return (
      <main className={pageStyles.centered}>
        <div className={pageStyles.statusMessage}>{loadingLabel}</div>
      </main>
    );
  }

  return <>{children}</>;
};
