"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import {
  getErrorMessage,
  getGoogleLoginUrl,
  login,
  resendVerificationEmail,
} from "@/lib/api";
import { clearAuthSessionQueries } from "@/lib/auth-session-cache";
import { useFreshAuthSession } from "@/lib/auth/use-fresh-auth-session";
import {
  getRequestErrorMessage,
  isEmailNotVerifiedError,
  resolveRequestError,
} from "@/lib/request-errors";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { buttonStyles, formStyles, pageStyles, textStyles } from "@/lib/styles";

export const LoginClient = () => {
  const router = useRouter();
  const queryClient = useQueryClient();
  const searchParams = useSearchParams();
  const oauthError = searchParams.get("oauthError");

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [requiresEmailVerification, setRequiresEmailVerification] =
    useState(false);
  const [isResendingVerification, setIsResendingVerification] = useState(false);
  const [resendMessage, setResendMessage] = useState<string | null>(null);
  const [resendError, setResendError] = useState<string | null>(null);

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

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setRequiresEmailVerification(false);
    setResendMessage(null);
    setResendError(null);
    setIsSubmitting(true);

    try {
      await login({ email, password });
      clearAuthSessionQueries(queryClient);
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

  if (session.status === "verifying") {
    return (
      <main className={pageStyles.centered}>
        <div className={pageStyles.statusMessage}>Checking session...</div>
      </main>
    );
  }

  if (session.status === "authenticated") {
    return null;
  }

  const sessionError =
    session.status === "error" ? getErrorMessage(session.error) : null;

  return (
    <main className={pageStyles.centered}>
      <Card variant="auth">
        <h1 className={textStyles.pageTitle}>Sign in</h1>
        <p className={textStyles.description}>
          Continue to your job search tracker.
        </p>

        <LoginGoogleAction />

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
            <div className="flex items-center justify-between gap-3">
              <label className={textStyles.label}>Password</label>
              <a className={buttonStyles.link} href="/forgot-password">
                Forgot password?
              </a>
            </div>
            <Input
              value={password}
              onChange={(event) => handlePasswordChange(event.target.value)}
              type="password"
              variant="auth"
              required
            />
          </div>

          <LoginGoogleOAuthErrorMessage oauthError={oauthError} />
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

export const LoginGoogleAction = () => (
  <div className="mt-6 space-y-4">
    <a
      className="flex h-10 w-full items-center justify-center gap-[10px] rounded-xl border border-[#747775] bg-white px-3 text-sm font-medium text-[#1F1F1F] transition hover:bg-zinc-50"
      href={getGoogleLoginUrl()}
    >
      <GoogleIcon />
      Continue with Google
    </a>
    <div className="flex items-center gap-3 text-xs font-medium text-zinc-500">
      <span className="h-px flex-1 bg-zinc-200" />
      <span>or sign in with email</span>
      <span className="h-px flex-1 bg-zinc-200" />
    </div>
  </div>
);

export const LoginGoogleOAuthErrorMessage = ({
  oauthError,
}: {
  oauthError: string | null;
}) => {
  if (oauthError !== "google") {
    return null;
  }

  return (
    <div className={formStyles.error}>
      Could not sign in with Google. Please try again.
    </div>
  );
};

const GoogleIcon = () => (
  <svg
    aria-hidden="true"
    className="h-[18px] w-[18px] shrink-0"
    focusable="false"
    viewBox="0 0 18 18"
  >
    <path
      d="M17.64 9.204c0-.638-.057-1.251-.164-1.84H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.258h2.908c1.702-1.567 2.684-3.874 2.684-6.615z"
      fill="#4285F4"
    />
    <path
      d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.583-5.036-3.71H.957v2.332A8.997 8.997 0 0 0 9 18z"
      fill="#34A853"
    />
    <path
      d="M3.964 10.71A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.996 8.996 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.332z"
      fill="#FBBC05"
    />
    <path
      d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.89 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58z"
      fill="#EA4335"
    />
  </svg>
);

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
