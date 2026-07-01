const MAX_JOB_URL_LENGTH = 2048;
const explicitSchemePattern = /^[A-Za-z][A-Za-z0-9+.-]*:\/\//;
const ipv4HostPattern = /^(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?$/;

const looksLikeHostPath = (value: string): boolean => {
  const hostCandidate = value.split(/[/?#]/, 1)[0];
  if (!hostCandidate || hostCandidate.includes(" ")) {
    return false;
  }

  const hostWithoutPort = hostCandidate.replace(/:\d+$/, "");
  if (hostCandidate.includes(":") && hostWithoutPort === hostCandidate) {
    return false;
  }

  return (
    hostWithoutPort.toLowerCase() === "localhost" ||
    hostWithoutPort.includes(".") ||
    ipv4HostPattern.test(hostCandidate)
  );
};

export const normalizeAiJobUrlInput = (
  value: string,
): { jobUrl: string; error: null } | { jobUrl: null; error: string } => {
  const trimmedValue = value.trim();

  if (!trimmedValue) {
    return { jobUrl: null, error: "Enter a job URL." };
  }

  if (trimmedValue.length > MAX_JOB_URL_LENGTH) {
    return { jobUrl: null, error: "Job URL must be 2048 characters or fewer." };
  }

  const candidate = explicitSchemePattern.test(trimmedValue)
    ? trimmedValue
    : looksLikeHostPath(trimmedValue)
      ? `https://${trimmedValue}`
      : null;

  if (!candidate) {
    return { jobUrl: null, error: "Enter a valid http or https job URL." };
  }

  try {
    const url = new URL(candidate);
    if (!["http:", "https:"].includes(url.protocol) || !url.hostname) {
      return { jobUrl: null, error: "Enter a valid http or https job URL." };
    }

    return { jobUrl: url.toString(), error: null };
  } catch {
    return { jobUrl: null, error: "Enter a valid http or https job URL." };
  }
};
