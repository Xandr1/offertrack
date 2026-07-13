export const webEnvironmentNames = [
  "local",
  "development",
  "test",
  "e2e",
  "staging",
  "production",
] as const;

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

const DEFAULT_LOCAL_API_URL = "http://localhost:8080";
const protectedEnvironments = new Set<WebEnvironmentName>([
  "staging",
  "production",
]);

const isLoopbackHost = (hostname: string): boolean => {
  const normalized = hostname
    .toLowerCase()
    .replace(/^\[|\]$/g, "")
    .replace(/\.$/, "");

  return (
    normalized === "localhost" ||
    normalized.endsWith(".localhost") ||
    normalized === "::" ||
    normalized === "::1" ||
    normalized === "0:0:0:0:0:0:0:0" ||
    normalized === "0:0:0:0:0:0:0:1" ||
    normalized.startsWith("::ffff:127.") ||
    normalized.startsWith("::ffff:7f") ||
    normalized.startsWith("0:0:0:0:0:ffff:7f") ||
    normalized === "0.0.0.0" ||
    normalized.startsWith("127.")
  );
};

const parseEnvironmentName = (
  rawValue: string | undefined,
  requireExplicit: boolean,
): WebEnvironmentName => {
  if (!rawValue) {
    if (requireExplicit) {
      throw new Error(
        "APP_ENV must be set for Next.js builds and runtime startup.",
      );
    }

    return "local";
  }

  if (!webEnvironmentNames.includes(rawValue as WebEnvironmentName)) {
    throw new Error(
      `APP_ENV must be one of: ${webEnvironmentNames.join(", ")}.`,
    );
  }

  return rawValue as WebEnvironmentName;
};

export const validateWebEnvironment = (
  input: WebEnvironmentInput,
  options: ValidationOptions = {},
): WebEnvironment => {
  const requireExplicit = options.requireExplicit ?? false;
  const appEnv = parseEnvironmentName(input.APP_ENV, requireExplicit);
  const rawApiUrl = input.NEXT_PUBLIC_API_URL;

  if (!rawApiUrl && requireExplicit) {
    throw new Error(
      "NEXT_PUBLIC_API_URL must be set for Next.js builds and runtime startup.",
    );
  }

  let parsedUrl: URL;

  try {
    parsedUrl = new URL(rawApiUrl ?? DEFAULT_LOCAL_API_URL);
  } catch {
    throw new Error("NEXT_PUBLIC_API_URL must be an absolute HTTP(S) URL.");
  }

  if (parsedUrl.protocol !== "http:" && parsedUrl.protocol !== "https:") {
    throw new Error("NEXT_PUBLIC_API_URL must be an absolute HTTP(S) URL.");
  }

  if (parsedUrl.username || parsedUrl.password) {
    throw new Error("NEXT_PUBLIC_API_URL must not include credentials.");
  }

  if (parsedUrl.search || parsedUrl.hash) {
    throw new Error("NEXT_PUBLIC_API_URL must not include a query or fragment.");
  }

  if (protectedEnvironments.has(appEnv)) {
    if (parsedUrl.protocol !== "https:") {
      throw new Error(
        "NEXT_PUBLIC_API_URL must use HTTPS in protected environments.",
      );
    }

    if (isLoopbackHost(parsedUrl.hostname)) {
      throw new Error(
        "NEXT_PUBLIC_API_URL must not use localhost or a loopback address in protected environments.",
      );
    }
  }

  return {
    appEnv,
    apiUrl: parsedUrl.toString().replace(/\/$/, ""),
  };
};

export const validateWebBuildEnvironment = (
  input: WebEnvironmentInput,
): WebEnvironment => {
  return validateWebEnvironment(input, { requireExplicit: true });
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
