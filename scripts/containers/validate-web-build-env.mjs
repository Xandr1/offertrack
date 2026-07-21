#!/usr/bin/env node

import webEnvironmentValidation from "../../apps/web/src/lib/web-environment-validation.cjs";

const { validateWebBuildEnvironment } = webEnvironmentValidation;

if (process.argv.length !== 4) {
  console.error("usage: validate-web-build-env.mjs APP_ENV NEXT_PUBLIC_API_URL");
  process.exit(2);
}

try {
  const environment = validateWebBuildEnvironment({
    APP_ENV: process.argv[2],
    NEXT_PUBLIC_API_URL: process.argv[3],
  });
  console.log(
    `Validated Web build environment ${environment.appEnv} with ${environment.apiUrl}.`,
  );
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(2);
}
