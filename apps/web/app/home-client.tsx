"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { clearAuthSessionQueries } from "@/lib/auth-session-cache";
import { useFreshAuthSession } from "@/lib/auth/use-fresh-auth-session";
import { LandingContent } from "./landing-content";

export const HomeClient = () => {
  const router = useRouter();
  const queryClient = useQueryClient();

  const session = useFreshAuthSession();

  useEffect(() => {
    if (session.status === "authenticated") {
      router.replace("/dashboard");
    }
  }, [router, session.status]);

  useEffect(() => {
    if (session.status === "unauthenticated") {
      clearAuthSessionQueries(queryClient);
    }
  }, [queryClient, session.status]);

  if (session.status === "authenticated") {
    return null;
  }

  return <LandingContent />;
};
