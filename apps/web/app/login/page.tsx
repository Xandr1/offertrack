"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  getCurrentUser,
  getErrorMessage,
  login,
  resendVerificationEmail,
} from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import {
  getRequestErrorMessage,
  isAuthError,
  isEmailNotVerifiedError,
  resolveRequestError,
} from "@/lib/request-errors";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { buttonStyles, formStyles, pageStyles, textStyles } from "@/lib/styles";

const LoginPage = () => {
  const router = useRouter();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [requiresEmailVerification, setRequiresEmailVerification] =
    useState(false);
  const [isResendingVerification, setIsResendingVerification] = useState(false);
  const [resendMessage, setResendMessage] = useState<string | null>(null);
  const [resendError, setResendError] = useState<string | null>(null);

  const sessionQuery = useQuery({
    queryKey: queryKeys.authMe,
    queryFn: getCurrentUser,
    retry: false,
  });

  useEffect(() => {
    if (sessionQuery.data) {
      router.replace("/dashboard");
    }
  }, [sessionQuery.data, router]);

  const isSessionAuthError = isAuthError(sessionQuery.error);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setRequiresEmailVerification(false);
    setResendMessage(null);
    setResendError(null);
    setIsSubmitting(true);

    try {
      await login({ email, password });
      router.push("/dashboard");
    } catch (requestError) {
      if (isEmailNotVerifiedError(requestError)) {
        setRequiresEmailVerification(true);
        return;
      }

      const resolvedError = await resolveRequestError(requestError);
      setError(resolvedError.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResendVerification = async () => {
    setIsResendingVerification(true);
    setResendMessage(null);
    setResendError(null);

    try {
      await resendVerificationEmail({ email });
      setResendMessage("Another verification email has been sent.");
    } catch (requestError) {
      setResendError(getRequestErrorMessage(requestError));
    } finally {
      setIsResendingVerification(false);
    }
  };

  const handleEmailChange = (value: string) => {
    setEmail(value);
    setRequiresEmailVerification(false);
    setResendMessage(null);
    setResendError(null);
  };

  const handlePasswordChange = (value: string) => {
    setPassword(value);
    setRequiresEmailVerification(false);
    setResendMessage(null);
    setResendError(null);
  };

  if (sessionQuery.isPending) {
    return (
      <main className={pageStyles.centered}>
        <div className={pageStyles.statusMessage}>Checking session...</div>
      </main>
    );
  }

  if (sessionQuery.data) {
    return null;
  }

  const sessionError =
    sessionQuery.error && !isSessionAuthError
      ? getErrorMessage(sessionQuery.error)
      : null;

  return (
    <main className={pageStyles.centered}>
      <Card variant="auth">
        <h1 className={textStyles.pageTitle}>Sign in</h1>
        <p className={textStyles.description}>
          Continue to your job search tracker.
        </p>

        <form onSubmit={handleSubmit} className={pageStyles.authForm}>
          <div>
            <label className={textStyles.label}>Email</label>
            <Input
              value={email}
              onChange={(event) => handleEmailChange(event.target.value)}
              type="email"
              variant="auth"
              required
            />
          </div>

          <div>
            <label className={textStyles.label}>Password</label>
            <Input
              value={password}
              onChange={(event) => handlePasswordChange(event.target.value)}
              type="password"
              variant="auth"
              required
            />
          </div>

          {sessionError && <div className={formStyles.error}>{sessionError}</div>}
          {error && <div className={formStyles.error}>{error}</div>}
          {requiresEmailVerification && (
            <LoginEmailVerificationMessage
              isResending={isResendingVerification}
              resendError={resendError}
              resendMessage={resendMessage}
              onResend={handleResendVerification}
            />
          )}

          <Button variant="primary" disabled={isSubmitting} type="submit">
            {isSubmitting ? "Signing in..." : "Sign in"}
          </Button>
        </form>

        <p className={pageStyles.authFooter}>
          No account?{" "}
          <a className={buttonStyles.link} href="/register">
            Create one
          </a>
        </p>
      </Card>
    </main>
  );
};

export default LoginPage;

type LoginEmailVerificationMessageProps = {
  isResending: boolean;
  resendError: string | null;
  resendMessage: string | null;
  onResend: () => void;
};

export const LoginEmailVerificationMessage = ({
  isResending,
  resendError,
  resendMessage,
  onResend,
}: LoginEmailVerificationMessageProps) => (
  <div className="rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
    <p>Please verify your email before signing in.</p>
    <Button
      className="mt-3"
      disabled={isResending}
      onClick={onResend}
      variant="secondary"
    >
      {isResending ? "Sending..." : "Resend verification email"}
    </Button>
    {resendMessage && <p className="mt-2 text-amber-900">{resendMessage}</p>}
    {resendError && <p className="mt-2 text-red-700">{resendError}</p>}
  </div>
);
