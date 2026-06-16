"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getCurrentUser } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { LandingContent } from "./landing-content";

export const HomeClient = () => {
  const router = useRouter();

  const sessionQuery = useQuery({
    queryKey: queryKeys.authMe,
    queryFn: getCurrentUser,
    retry: false,
  });

  useEffect(() => {
    if (sessionQuery.data) {
      router.replace("/dashboard");
    }
  }, [router, sessionQuery.data]);

  if (sessionQuery.data) {
    return null;
  }

  return <LandingContent />;
};
