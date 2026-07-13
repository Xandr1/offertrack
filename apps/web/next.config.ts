import path from "node:path";
import { fileURLToPath } from "node:url";
import type { NextConfig } from "next";
import { PHASE_DEVELOPMENT_SERVER } from "next/constants";
import {
  validateWebBuildEnvironment,
  validateWebEnvironment,
} from "./src/lib/web-environment";

const webRoot = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(webRoot, "../..");

const createNextConfig = (isProtected: boolean): NextConfig => ({
  turbopack: {
    // Use the monorepo root so pnpm workspace dependencies are resolvable.
    root: repoRoot,
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          {
            key: "Referrer-Policy",
            value: "strict-origin-when-cross-origin",
          },
          { key: "X-Frame-Options", value: "DENY" },
          {
            key: "Permissions-Policy",
            value:
              "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
          },
          ...(isProtected
            ? [
                {
                  key: "Strict-Transport-Security",
                  value: "max-age=31536000; includeSubDomains",
                },
              ]
            : []),
        ],
      },
    ];
  },
});

const configureNext = (phase: string): NextConfig => {
  const environmentInput = {
    APP_ENV: process.env.APP_ENV,
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL,
  };
  const environment =
    phase === PHASE_DEVELOPMENT_SERVER
      ? validateWebEnvironment(environmentInput)
      : validateWebBuildEnvironment(environmentInput);

  return createNextConfig(
    environment.appEnv === "staging" || environment.appEnv === "production",
  );
};

export default configureNext;
