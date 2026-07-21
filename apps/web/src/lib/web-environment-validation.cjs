const webEnvironmentNames = [
  "local",
  "development",
  "test",
  "e2e",
  "staging",
  "production",
];

const DEFAULT_LOCAL_API_URL = "http://localhost:8080";
const protectedEnvironments = new Set(["staging", "production"]);

const isUnsafeProtectedHost = (hostname) => {
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
    normalized === "::7f00:1" ||
    normalized === "0:0:0:0:0:0:7f00:1" ||
    normalized.startsWith("::ffff:") ||
    normalized.startsWith("0:0:0:0:0:ffff:") ||
    normalized === "0.0.0.0" ||
    normalized.startsWith("127.")
  );
};

const parseEnvironmentName = (rawValue, requireExplicit) => {
  if (!rawValue) {
    if (requireExplicit) {
      throw new Error(
        "APP_ENV must be set for Next.js builds and runtime startup.",
      );
    }

    return "local";
  }

  if (!webEnvironmentNames.includes(rawValue)) {
    throw new Error(
      `APP_ENV must be one of: ${webEnvironmentNames.join(", ")}.`,
    );
  }

  return rawValue;
};

const validateWebEnvironment = (input, options = {}) => {
  const requireExplicit = options.requireExplicit ?? false;
  const appEnv = parseEnvironmentName(input.APP_ENV, requireExplicit);
  const rawApiUrl = input.NEXT_PUBLIC_API_URL;

  if (!rawApiUrl && requireExplicit) {
    throw new Error(
      "NEXT_PUBLIC_API_URL must be set for Next.js builds and runtime startup.",
    );
  }

  if (rawApiUrl?.includes("?") || rawApiUrl?.includes("#")) {
    throw new Error("NEXT_PUBLIC_API_URL must not include a query or fragment.");
  }

  let parsedUrl;
  try {
    parsedUrl = new URL(rawApiUrl ?? DEFAULT_LOCAL_API_URL);
  } catch {
    throw new Error("NEXT_PUBLIC_API_URL must be an absolute HTTP(S) URL.");
  }

  if (parsedUrl.protocol !== "http:" && parsedUrl.protocol !== "https:") {
    throw new Error("NEXT_PUBLIC_API_URL must be an absolute HTTP(S) URL.");
  }
  const authority = (rawApiUrl ?? DEFAULT_LOCAL_API_URL)
    .split("://", 2)[1]
    ?.split(/[/?#]/, 1)[0];
  if (parsedUrl.username || parsedUrl.password || authority?.includes("@")) {
    throw new Error("NEXT_PUBLIC_API_URL must not include credentials.");
  }

  if (protectedEnvironments.has(appEnv)) {
    if (parsedUrl.protocol !== "https:") {
      throw new Error(
        "NEXT_PUBLIC_API_URL must use HTTPS in protected environments.",
      );
    }
    if (isUnsafeProtectedHost(parsedUrl.hostname)) {
      throw new Error(
        "NEXT_PUBLIC_API_URL must not use localhost or a loopback address, " +
          "an unspecified address, or an IPv4-mapped IPv6 address in protected environments.",
      );
    }
  }

  return {
    appEnv,
    apiUrl: parsedUrl.toString().replace(/\/$/, ""),
  };
};

const validateWebBuildEnvironment = (input) =>
  validateWebEnvironment(input, { requireExplicit: true });

module.exports = {
  validateWebBuildEnvironment,
  validateWebEnvironment,
  webEnvironmentNames,
};
