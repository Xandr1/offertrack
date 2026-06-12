import { Suspense } from "react";
import { pageStyles } from "@/lib/styles";
import { VerifyEmailClient, VerifyEmailStatus } from "./verify-email-client";

export default function VerifyEmailPage() {
  return (
    <Suspense
      fallback={
        <main className={pageStyles.centered}>
          <VerifyEmailStatus status="verifying" />
        </main>
      }
    >
      <VerifyEmailClient />
    </Suspense>
  );
}
