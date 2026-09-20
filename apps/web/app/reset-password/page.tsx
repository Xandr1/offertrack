import { Suspense } from "react";
import { pageStyles } from "@/lib/styles";
import { ResetPasswordClient } from "./reset-password-client";

export default function ResetPasswordPage() {
  return (
    <Suspense
      fallback={
        <main className={pageStyles.centered}>
          Checking your reset link.
        </main>
      }
    >
      <ResetPasswordClient />
    </Suspense>
  );
}
