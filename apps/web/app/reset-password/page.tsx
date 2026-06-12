import { Suspense } from "react";
import { pageStyles } from "@/lib/styles";
import { ResetPasswordClient, ResetPasswordStatus } from "./reset-password-client";

export default function ResetPasswordPage() {
  return (
    <Suspense
      fallback={
        <main className={pageStyles.centered}>
          <ResetPasswordStatus status="missing-token" />
        </main>
      }
    >
      <ResetPasswordClient />
    </Suspense>
  );
}
