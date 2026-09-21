"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { getPasswordValidationMessage, register } from "@/lib/api";
import { getRequestErrorMessage, resolveRequestError } from "@/lib/request-errors";
import { clearAuthSessionQueries } from "@/lib/auth-session-cache";
import { useFreshAuthSession } from "@/lib/auth/use-fresh-auth-session";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { buttonStyles, formStyles, pageStyles, textStyles } from "@/lib/styles";

export default function RegisterPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const session = useFreshAuthSession();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);

  useEffect(() => {
    if (session.status === "authenticated") router.replace("/dashboard");
    if (session.status === "unauthenticated") clearAuthSessionQueries(queryClient);
  }, [queryClient, router, session.status]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (session.status !== "unauthenticated" || isSubmitting) return;
    setError(null);
    setIsSubmitting(true);

    try {
      await register({ name, email, password });
      setRegisteredEmail(email.trim());
    } catch (requestError) {
      const resolvedError = await resolveRequestError(requestError);
      setError(getPasswordValidationMessage(requestError, password.length) ?? resolvedError.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  if (session.status === "verifying") {
    return (
      <main className={pageStyles.centered}>
        <div className={pageStyles.statusMessage}>Checking session...</div>
      </main>
    );
  }

  if (session.status === "authenticated") return null;

  if (session.status === "error") {
    return (
      <main className={pageStyles.centered}>
        <Card variant="auth">
          <h1 className={textStyles.pageTitle}>Could not check your session</h1>
          <p className={formStyles.error} role="alert">{getRequestErrorMessage(session.error)}</p>
          <Button className="mt-4" onClick={session.retry} variant="secondary">Retry</Button>
        </Card>
      </main>
    );
  }

  if (registeredEmail) {
    return (
      <main className={pageStyles.centered}>
        <RegisterSuccessState email={registeredEmail} />
      </main>
    );
  }

  return (
    <main className={pageStyles.centered}>
      <Card variant="auth">
        <h1 className={textStyles.pageTitle}>Create account</h1>
        <p className={textStyles.description}>
          Start tracking job applications without building a spreadsheet
          monster.
        </p>

        <form onSubmit={handleSubmit} className={pageStyles.authForm}>
          <div>
            <label className={textStyles.label} htmlFor="register-name">Name</label>
            <Input
              id="register-name"
              autoComplete="name"
              maxLength={100}
              value={name}
              onChange={(event) => setName(event.target.value)}
              type="text"
              variant="auth"
            />
          </div>

          <div>
            <label className={textStyles.label} htmlFor="register-email">Email</label>
            <Input
              id="register-email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              type="email"
              variant="auth"
              required
            />
          </div>

          <div>
            <label className={textStyles.label} htmlFor="register-password">Password</label>
            <Input
              id="register-password"
              autoComplete="new-password"
              aria-describedby="register-password-help"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              variant="auth"
              required
            />
            <p className={textStyles.helper} id="register-password-help">
              Use 8–64 characters, including an uppercase letter, a lowercase letter, and a number.
            </p>
          </div>

          {error && <div className={formStyles.error} role="alert">{error}</div>}

          <Button variant="primary" disabled={isSubmitting} type="submit">
            {isSubmitting ? "Creating account..." : "Create account"}
          </Button>
        </form>

        <p className={pageStyles.authFooter}>
          Already have an account?{" "}
          <a className={buttonStyles.link} href="/login">
            Sign in
          </a>
        </p>
      </Card>
    </main>
  );
}

type RegisterSuccessStateProps = {
  email: string;
};

export const RegisterSuccessState = ({ email }: RegisterSuccessStateProps) => (
  <Card variant="auth">
    <h1 className={textStyles.pageTitle}>Check your email</h1>
    <p className={textStyles.description}>
      We sent a verification link to {email}. Verify your email before signing
      in.
    </p>
    <p className={pageStyles.authFooter}>
      Already verified?{" "}
      <a className={buttonStyles.link} href="/login">
        Sign in
      </a>
    </p>
  </Card>
);
