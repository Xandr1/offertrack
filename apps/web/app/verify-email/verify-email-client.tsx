"use client";

import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { verifyEmail } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { getRequestErrorMessage } from "@/lib/request-errors";
import { buttonStyles, formStyles, pageStyles, textStyles } from "@/lib/styles";
import { Card } from "@/components/ui/card";

export const VerifyEmailClient = () => {
  const searchParams = useSearchParams();
  const token = searchParams.get("token")?.trim() ?? "";

  const verificationQuery = useQuery({
    queryKey: queryKeys.emailVerification(token),
    queryFn: () => verifyEmail({ token }),
    enabled: Boolean(token),
    retry: false,
    refetchOnMount: false,
    refetchOnReconnect: false,
    refetchOnWindowFocus: false,
    staleTime: Infinity,
  });

  if (!token) {
    return (
      <main className={pageStyles.centered}>
        <VerifyEmailStatus status="missing-token" />
      </main>
    );
  }

  if (verificationQuery.isSuccess) {
    return (
      <main className={pageStyles.centered}>
        <VerifyEmailStatus status="success" />
      </main>
    );
  }

  if (verificationQuery.error) {
    return (
      <main className={pageStyles.centered}>
        <VerifyEmailStatus
          errorMessage={getRequestErrorMessage(verificationQuery.error)}
          status="error"
        />
      </main>
    );
  }

  return (
    <main className={pageStyles.centered}>
      <VerifyEmailStatus status="verifying" />
    </main>
  );
};

export type VerifyEmailStatusType =
  | "missing-token"
  | "verifying"
  | "success"
  | "error";

type VerifyEmailStatusProps = {
  errorMessage?: string;
  status: VerifyEmailStatusType;
};

export const VerifyEmailStatus = ({
  errorMessage,
  status,
}: VerifyEmailStatusProps) => {
  if (status === "verifying") {
    return (
      <Card variant="auth">
        <h1 className={textStyles.pageTitle}>Verifying email</h1>
        <p className={textStyles.description}>Checking your verification link.</p>
      </Card>
    );
  }

  if (status === "success") {
    return (
      <Card variant="auth">
        <h1 className={textStyles.pageTitle}>Email verified</h1>
        <p className={textStyles.description}>
          Your email is verified. You can now sign in.
        </p>
        <p className={pageStyles.authFooter}>
          <a className={buttonStyles.link} href="/login">
            Sign in
          </a>
        </p>
      </Card>
    );
  }

  if (status === "missing-token") {
    return (
      <Card variant="auth">
        <h1 className={textStyles.pageTitle}>Verification link missing</h1>
        <div className="mt-4">
          <div className={formStyles.error}>
            Open the verification link from your email.
          </div>
        </div>
      </Card>
    );
  }

  return (
    <Card variant="auth">
      <h1 className={textStyles.pageTitle}>Could not verify email</h1>
      <div className="mt-4">
        <div className={formStyles.error}>
          {errorMessage ?? "Verification link is invalid or expired."}
        </div>
      </div>
      <p className={pageStyles.authFooter}>
        <a className={buttonStyles.link} href="/login">
          Back to sign in
        </a>
      </p>
    </Card>
  );
};
