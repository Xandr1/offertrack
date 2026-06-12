"use client";

import { FormEvent, useState } from "react";
import { useSearchParams } from "next/navigation";
import { resetPassword } from "@/lib/api";
import { getRequestErrorMessage } from "@/lib/request-errors";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { buttonStyles, formStyles, pageStyles, textStyles } from "@/lib/styles";

type ResetPasswordFormProps = {
  token: string;
};

export const ResetPasswordClient = () => {
  const searchParams = useSearchParams();
  const token = searchParams.get("token")?.trim() ?? "";

  return (
    <main className={pageStyles.centered}>
      <ResetPasswordForm token={token} />
    </main>
  );
};

export const ResetPasswordForm = ({ token }: ResetPasswordFormProps) => {
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isReset, setIsReset] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);

    if (!token) {
      setError("Open the password reset link from your email.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setIsSubmitting(true);

    try {
      await resetPassword({ token, newPassword });
      setIsReset(true);
    } catch (requestError) {
      setError(getRequestErrorMessage(requestError));
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!token) {
    return <ResetPasswordStatus status="missing-token" />;
  }

  if (isReset) {
    return <ResetPasswordStatus status="success" />;
  }

  return (
    <Card variant="auth">
      <h1 className={textStyles.pageTitle}>Choose a new password</h1>
      <p className={textStyles.description}>
        Enter a new password for your OfferTrack account.
      </p>

      <form onSubmit={handleSubmit} className={pageStyles.authForm}>
        <div>
          <label className={textStyles.label} htmlFor="reset-password-new">
            New password
          </label>
          <Input
            id="reset-password-new"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            type="password"
            variant="auth"
            required
          />
          <p className={textStyles.helper}>
            At least 8 characters, with uppercase, lowercase, and a digit.
          </p>
        </div>

        <div>
          <label className={textStyles.label} htmlFor="reset-password-confirm">
            Confirm password
          </label>
          <Input
            id="reset-password-confirm"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
            type="password"
            variant="auth"
            required
          />
        </div>

        {error && <div className={formStyles.error}>{error}</div>}

        <Button variant="primary" disabled={isSubmitting} type="submit">
          {isSubmitting ? "Resetting..." : "Reset password"}
        </Button>
      </form>

      <p className={pageStyles.authFooter}>
        <a className={buttonStyles.link} href="/login">
          Back to sign in
        </a>
      </p>
    </Card>
  );
};

type ResetPasswordStatusProps = {
  status: "missing-token" | "success";
};

export const ResetPasswordStatus = ({ status }: ResetPasswordStatusProps) => {
  if (status === "success") {
    return (
      <Card variant="auth">
        <h1 className={textStyles.pageTitle}>Password reset</h1>
        <p className={textStyles.description}>
          Your password has been reset. You can now sign in.
        </p>
        <p className={pageStyles.authFooter}>
          <a className={buttonStyles.link} href="/login">
            Sign in
          </a>
        </p>
      </Card>
    );
  }

  return (
    <Card variant="auth">
      <h1 className={textStyles.pageTitle}>Reset link missing</h1>
      <div className="mt-4">
        <div className={formStyles.error}>
          Open the password reset link from your email.
        </div>
      </div>
      <p className={pageStyles.authFooter}>
        <a className={buttonStyles.link} href="/forgot-password">
          Request a new link
        </a>
      </p>
    </Card>
  );
};
