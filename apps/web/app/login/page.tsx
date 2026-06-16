import { Suspense } from "react";
import { pageStyles } from "@/lib/styles";
import { LoginClient } from "./login-client";

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <main className={pageStyles.centered}>
          <div className={pageStyles.statusMessage}>Checking session...</div>
        </main>
      }
    >
      <LoginClient />
    </Suspense>
  );
}
