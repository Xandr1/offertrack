"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api";
import { redirectToLoginIfProtectedRoute } from "@/lib/request-errors";

export const useRedirectToLoginOnProtectedError = (
  error: unknown,
): boolean => {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [didRedirect, setDidRedirect] = useState(false);
  const handledErrorRef = useRef<unknown>(undefined);
  const isCheckingRedirectRef = useRef(false);
  const isMountedRef = useRef(true);
  const isRedirectingRef = useRef(false);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    if (!error) {
      if (!isCheckingRedirectRef.current && !isRedirectingRef.current) {
        handledErrorRef.current = undefined;
      }
      queueMicrotask(() => {
        if (isMountedRef.current && !isRedirectingRef.current) {
          setDidRedirect(false);
        }
      });
      return;
    }

    if (
      isRedirectingRef.current ||
      isCheckingRedirectRef.current ||
      handledErrorRef.current === error
    ) {
      return;
    }

    handledErrorRef.current = error;
    isCheckingRedirectRef.current = true;

    if (error instanceof ApiError && error.status === 401) {
      isRedirectingRef.current = true;
    }

    void redirectToLoginIfProtectedRoute(error, router, queryClient).then(
      (redirected) => {
        isCheckingRedirectRef.current = false;
        if (redirected) {
          isRedirectingRef.current = true;
        }
        if (isMountedRef.current) {
          setDidRedirect(redirected);
        }
      },
    );
  }, [error, queryClient, router]);

  const isImmediateUnauthorized =
    error instanceof ApiError && error.status === 401;

  return isImmediateUnauthorized || didRedirect;
};
