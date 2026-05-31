"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { getCurrentUser, getErrorMessage, login } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { isAuthError, resolveRequestError } from "@/lib/request-errors";
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
    setIsSubmitting(true);

    try {
      await login({ email, password });
      router.push("/dashboard");
    } catch (requestError) {
      const resolvedError = await resolveRequestError(requestError);
      setError(resolvedError.message);
    } finally {
      setIsSubmitting(false);
    }
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
          </div>

          {sessionError && <div className={formStyles.error}>{sessionError}</div>}
          {error && <div className={formStyles.error}>{error}</div>}

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
