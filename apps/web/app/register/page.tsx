"use client";

import { FormEvent, useState } from "react";
import { register } from "@/lib/api";
import { resolveRequestError } from "@/lib/request-errors";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { buttonStyles, formStyles, pageStyles, textStyles } from "@/lib/styles";

export default function RegisterPage() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      await register({ name, email, password });
      setRegisteredEmail(email.trim());
    } catch (requestError) {
      const resolvedError = await resolveRequestError(requestError);
      setError(resolvedError.message);
    } finally {
      setIsSubmitting(false);
    }
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
            <label className={textStyles.label}>Name</label>
            <Input
              value={name}
              onChange={(event) => setName(event.target.value)}
              type="text"
              variant="auth"
            />
          </div>

          <div>
            <label className={textStyles.label}>Email</label>
            <Input
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              type="email"
              variant="auth"
              required
            />
          </div>

          <div>
            <label className={textStyles.label}>Password</label>
            <Input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              variant="auth"
              required
            />
            <p className={textStyles.helper}>
              At least 8 characters, with uppercase, lowercase, and a digit.
            </p>
          </div>

          {error && <div className={formStyles.error}>{error}</div>}

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
