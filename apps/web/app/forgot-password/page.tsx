"use client";

import { FormEvent, useState } from "react";
import { forgotPassword } from "@/lib/api";
import { getRequestErrorMessage } from "@/lib/request-errors";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { buttonStyles, formStyles, pageStyles, textStyles } from "@/lib/styles";

export const PASSWORD_RESET_REQUEST_MESSAGE =
  "If an account exists for that email, we sent password reset instructions.";

export default function ForgotPasswordPage() {
  return (
    <main className={pageStyles.centered}>
      <ForgotPasswordForm />
    </main>
  );
}

export const ForgotPasswordForm = () => {
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      await forgotPassword({ email });
      setIsSubmitted(true);
    } catch (requestError) {
      setError(getRequestErrorMessage(requestError));
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isSubmitted) {
    return <ForgotPasswordSuccessState />;
  }

  return (
    <Card variant="auth">
      <h1 className={textStyles.pageTitle}>Reset password</h1>
      <p className={textStyles.description}>
        Enter your account email to receive a reset link.
      </p>

      <form onSubmit={handleSubmit} className={pageStyles.authForm}>
        <div>
          <label className={textStyles.label} htmlFor="forgot-password-email">
            Email
          </label>
          <Input
            id="forgot-password-email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            type="email"
            variant="auth"
            required
          />
        </div>

        {error && <div className={formStyles.error}>{error}</div>}

        <Button variant="primary" disabled={isSubmitting} type="submit">
          {isSubmitting ? "Sending..." : "Send reset link"}
        </Button>
      </form>

      <p className={pageStyles.authFooter}>
        Remembered your password?{" "}
        <a className={buttonStyles.link} href="/login">
          Sign in
        </a>
      </p>
    </Card>
  );
};

export const ForgotPasswordSuccessState = () => (
  <Card variant="auth">
    <h1 className={textStyles.pageTitle}>Check your email</h1>
    <p className={textStyles.description}>{PASSWORD_RESET_REQUEST_MESSAGE}</p>
    <p className={pageStyles.authFooter}>
      <a className={buttonStyles.link} href="/login">
        Back to sign in
      </a>
    </p>
  </Card>
);
