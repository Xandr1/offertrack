"use client";

import {
  createContext,
  ReactNode,
  useContext,
  useEffect,
} from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import type { UserSummary } from "@/lib/api";
import { clearAuthSessionQueries } from "@/lib/auth-session-cache";
import { useFreshAuthSession } from "@/lib/auth/use-fresh-auth-session";
import { getRequestErrorMessage } from "@/lib/request-errors";
import { formStyles, pageStyles, textStyles } from "@/lib/styles";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

const ProtectedUserContext = createContext<UserSummary | null>(null);

type ProtectedRouteProps = {
  children: ReactNode;
  errorTitle: string;
  loadingLabel: string;
};

export const useProtectedUser = (): UserSummary => {
  const user = useContext(ProtectedUserContext);

  if (!user) {
    throw new Error("useProtectedUser must be used inside ProtectedRoute");
  }

  return user;
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

  if (session.status !== "authenticated") {
    return null;
  }

  return (
    <ProtectedUserContext.Provider key={session.user.id} value={session.user}>
      {children}
    </ProtectedUserContext.Provider>
  );
};
