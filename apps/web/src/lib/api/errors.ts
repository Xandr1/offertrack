import { apiErrorResponseSchema } from "./schemas";

export const EMAIL_NOT_VERIFIED_ERROR_CODE = "EMAIL_NOT_VERIFIED";
export const INVALID_AUTH_TOKEN_ERROR_CODE = "INVALID_AUTH_TOKEN";

const aiDraftErrorMessages: Record<string, string> = {
  AI_SERVICE_EXTRACTION_FAILED:
    "We couldn't generate a draft from that job page. Try another job URL.",
  AI_SERVICE_FETCH_FAILED:
    "We couldn't access this job page automatically. Check URL or try again.",
  AI_SERVICE_INVALID_URL: "Enter a valid job URL.",
  AI_SERVICE_TIMEOUT: "AI draft generation timed out. Try again.",
  AI_SERVICE_UNAVAILABLE:
    "AI draft generation is temporarily unavailable. Try again.",
};

export class ApiError extends Error {
  status: number;
  body: string;

  constructor(status: number, body: string) {
    super(body || `Request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

export class NetworkError extends Error {
  constructor() {
    super("Cannot connect to the server.");
    this.name = "NetworkError";
  }
}

export class ResponseValidationError extends Error {
  constructor() {
    super("Server returned an unexpected response.");
    this.name = "ResponseValidationError";
  }
}

const parseApiError = (error: unknown) => {
  if (!(error instanceof ApiError)) {
    return null;
  }

  try {
    const parsed = apiErrorResponseSchema.safeParse(JSON.parse(error.body));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
};

export const getApiErrorCode = (error: unknown): string | null =>
  parseApiError(error)?.code ?? null;

export const hasApiErrorCode = (error: unknown, code: string): boolean => {
  return getApiErrorCode(error) === code;
};

const validationMessages = (
  fields: { field: string; message: string }[],
  passwordLength?: number,
): string => [...new Set(fields.map(({ field, message }) => {
  if (field !== "password" && field !== "newPassword") return message;
  switch (message) {
    case "Password must be between 8 and 64 characters":
      if (passwordLength !== undefined && passwordLength < 8) return "Password must be at least 8 characters.";
      if (passwordLength !== undefined && passwordLength > 64) return "Password must be at most 64 characters.";
      return "Password must be between 8 and 64 characters.";
    case "Password must contain at least 1 lowercase letter, 1 uppercase letter and 1 digit":
      return "Password must include an uppercase letter, a lowercase letter, and a number.";
    case "Password must be at most 72 UTF-8 bytes":
      return "Password is too long.";
    default:
      return message;
  }
}))].join(" ");

/** Formats server validation feedback without changing which passwords are accepted. */
export const getPasswordValidationMessage = (error: unknown, passwordLength: number): string | null => {
  if (!(error instanceof ApiError) || error.status !== 400) return null;
  const response = parseApiError(error);
  return response?.code === "VALIDATION_ERROR" && response.fieldErrors?.length
    ? validationMessages(response.fieldErrors, passwordLength)
    : null;
};

export const getErrorMessage = (error: unknown): string => {
  if (error instanceof NetworkError) {
    return "Cannot connect to the server";
  }

  if (error instanceof ResponseValidationError) {
    return "Server returned an unexpected response.";
  }

  if (error instanceof ApiError) {
    const response = parseApiError(error);
    const code = response?.code;

    if (code === EMAIL_NOT_VERIFIED_ERROR_CODE) {
      return "Please verify your email before signing in.";
    }

    if (code === INVALID_AUTH_TOKEN_ERROR_CODE) {
      return "Link is invalid or expired.";
    }

    if (code && aiDraftErrorMessages[code]) {
      return aiDraftErrorMessages[code];
    }

    if (error.status === 400) {
      if (code === "VALIDATION_ERROR" && response?.fieldErrors?.length) {
        return validationMessages(response.fieldErrors);
      }
      return "Please check the form fields.";
    }

    if (error.status === 401 || error.status === 403) {
      return "Please sign in again.";
    }

    if (error.status === 409) {
      return "This email is already registered.";
    }

    if (error.status >= 500) {
      return "Something went wrong on the server. Try again.";
    }

    return "Request failed. Please try again.";
  }

  return "Something went wrong. Please try again.";
};
