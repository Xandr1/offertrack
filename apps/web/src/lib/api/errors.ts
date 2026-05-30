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

export const getErrorMessage = (error: unknown): string => {
  if (error instanceof NetworkError) {
    return "Cannot connect to the server. Check that the API is running.";
  }

  if (error instanceof ResponseValidationError) {
    return "Server returned an unexpected response.";
  }

  if (error instanceof ApiError) {
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
