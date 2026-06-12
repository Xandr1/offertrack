import { apiErrorResponseSchema } from "./schemas";

export const EMAIL_NOT_VERIFIED_ERROR_CODE = "EMAIL_NOT_VERIFIED";
export const INVALID_AUTH_TOKEN_ERROR_CODE = "INVALID_AUTH_TOKEN";

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

export const getApiErrorCode = (error: unknown): string | null => {
  if (!(error instanceof ApiError)) {
    return null;
  }

  try {
    const parsed = apiErrorResponseSchema.safeParse(JSON.parse(error.body));
    return parsed.success ? parsed.data.code : null;
  } catch {
    return null;
  }
};

export const hasApiErrorCode = (error: unknown, code: string): boolean => {
  return getApiErrorCode(error) === code;
};

export const getErrorMessage = (error: unknown): string => {
  if (error instanceof NetworkError) {
    return "Cannot connect to the server. Check that the API is running.";
  }

  if (error instanceof ResponseValidationError) {
    return "Server returned an unexpected response.";
  }

  if (error instanceof ApiError) {
    const code = getApiErrorCode(error);

    if (code === EMAIL_NOT_VERIFIED_ERROR_CODE) {
      return "Please verify your email before signing in.";
    }

    if (code === INVALID_AUTH_TOKEN_ERROR_CODE) {
      return "Verification link is invalid or expired.";
    }

    if (error.status === 400) {
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
