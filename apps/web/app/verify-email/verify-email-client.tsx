"use client";

import { useEffect, useRef, useState } from "react";
import { useRecoveryToken } from "@/lib/auth/use-recovery-token";
import { verifyEmail } from "@/lib/api";
import { getRequestErrorMessage } from "@/lib/request-errors";
import { buttonStyles, formStyles, pageStyles, textStyles } from "@/lib/styles";
import { Card } from "@/components/ui/card";

export const VerifyEmailClient = () => {
  const { ready, token } = useRecoveryToken();
  const pending = useRef<Promise<unknown> | null>(null);
  const [status, setStatus] = useState<VerifyEmailStatusType>("verifying");
  const [errorMessage, setErrorMessage] = useState<string>();
  useEffect(() => {
    if (!ready || !token) return;
    // React effect replay shares the same promise; never consume the link twice.
    pending.current ??= verifyEmail({ token });
    let mounted = true;
    void pending.current.then(
      () => { if (mounted) setStatus("success"); },
      error => {
        if (mounted) { setStatus("error"); setErrorMessage(getRequestErrorMessage(error)); }
      },
    );
    return () => { mounted = false; };
  }, [ready, token]);

  return (
    <main className={pageStyles.centered}>
      <VerifyEmailStatus status={!ready ? "verifying" : !token ? "missing-token" : status} errorMessage={errorMessage} />
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
