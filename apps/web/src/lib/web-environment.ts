import {
  validateWebBuildEnvironment as validateRuntimeWebBuildEnvironment,
  validateWebEnvironment as validateRuntimeWebEnvironment,
  webEnvironmentNames as runtimeWebEnvironmentNames,
} from "./web-environment-validation.cjs";

export const webEnvironmentNames = runtimeWebEnvironmentNames as unknown as readonly [
  "local",
  "development",
  "test",
  "e2e",
  "staging",
  "production",
];

export type WebEnvironmentName = (typeof webEnvironmentNames)[number];

type WebEnvironmentInput = {
  APP_ENV?: string;
  NEXT_PUBLIC_API_URL?: string;
};

type ValidationOptions = {
  requireExplicit?: boolean;
};

export type WebEnvironment = {
  appEnv: WebEnvironmentName;
  apiUrl: string;
};

export const validateWebEnvironment = (
  input: WebEnvironmentInput,
  options: ValidationOptions = {},
): WebEnvironment => {
  return validateRuntimeWebEnvironment(input, options) as WebEnvironment;
};

export const validateWebBuildEnvironment = (
  input: WebEnvironmentInput,
): WebEnvironment => {
  return validateRuntimeWebBuildEnvironment(input) as WebEnvironment;
};

export const getPublicApiUrl = (): string => {
  const rawApiUrl = process.env.NEXT_PUBLIC_API_URL;

  if (!rawApiUrl && process.env.NODE_ENV === "production") {
    throw new Error("NEXT_PUBLIC_API_URL must be set in production builds.");
  }

  // APP_ENV is intentionally server-only. next.config.ts validates it with the
  // same rules before a build/start can produce a client bundle; client code
  // only consumes the explicitly inlined NEXT_PUBLIC_API_URL.
  return validateWebEnvironment({
    APP_ENV: process.env.NODE_ENV === "test" ? "test" : "local",
    NEXT_PUBLIC_API_URL: rawApiUrl,
  }).apiUrl;
};
