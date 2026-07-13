import { z } from "zod";
import { getPublicApiUrl } from "../web-environment";
import {
  ApiError,
  getApiErrorCode,
  NetworkError,
  ResponseValidationError,
} from "./errors";

export const API_URL = getPublicApiUrl();

const CSRF_INVALID_ERROR_CODE = "CSRF_INVALID";
const csrfResponseSchema = z.object({
  token: z.string().min(1),
  headerName: z.literal("X-XSRF-TOKEN"),
});
const unsafeMethods = new Set(["POST", "PUT", "PATCH", "DELETE"]);

let csrfToken: string | null = null;
let csrfTokenRequest: Promise<string> | null = null;
let csrfStateVersion = 0;

export const clearCsrfToken = (): void => {
  csrfStateVersion += 1;
  csrfToken = null;
  csrfTokenRequest = null;
};

const clearCsrfTokenIfCurrent = (usedToken: string | undefined): void => {
  if (usedToken && csrfToken === usedToken) {
    clearCsrfToken();
  }
};

const fetchWithNetworkError = async (
  path: string,
  options: RequestInit,
): Promise<Response> => {
  try {
    return await fetch(`${API_URL}${path}`, {
      ...options,
      credentials: "include",
    });
  } catch {
    throw new NetworkError();
  }
};

const readSuccessfulJson = <T,>(
  response: Response,
  text: string,
  schema: z.ZodType<T>,
): T => {
  if (!response.ok) {
    throw new ApiError(response.status, text);
  }

  if (!text) {
    return undefined as T;
  }

  let json: unknown;

  try {
    json = JSON.parse(text);
  } catch {
    throw new ResponseValidationError();
  }

  const parsed = schema.safeParse(json);

  if (!parsed.success) {
    throw new ResponseValidationError();
  }

  return parsed.data;
};

const fetchCsrfToken = (): Promise<string> => {
  if (csrfToken) {
    return Promise.resolve(csrfToken);
  }

  if (csrfTokenRequest) {
    return csrfTokenRequest;
  }

  const requestVersion = csrfStateVersion;
  const pendingRequest = (async () => {
    const response = await fetchWithNetworkError("/auth/csrf", {
      method: "GET",
      headers: new Headers({ Accept: "application/json" }),
    });
    const text = await response.text();
    const result = readSuccessfulJson(response, text, csrfResponseSchema);

    if (requestVersion === csrfStateVersion) {
      csrfToken = result.token;
    }

    return result.token;
  })();

  csrfTokenRequest = pendingRequest;
  void pendingRequest.then(
    () => {
      if (csrfTokenRequest === pendingRequest) {
        csrfTokenRequest = null;
      }
    },
    () => {
      if (csrfTokenRequest === pendingRequest) {
        csrfTokenRequest = null;
      }
    },
  );

  return pendingRequest;
};

const buildHeaders = (
  options: RequestInit,
  requestCsrfToken?: string,
): Headers => {
  const headers = new Headers(options.headers);

  if (typeof options.body === "string" && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }

  if (requestCsrfToken) {
    headers.set("X-XSRF-TOKEN", requestCsrfToken);
  }

  return headers;
};

const performRequest = (
  path: string,
  options: RequestInit,
  requestCsrfToken?: string,
): Promise<Response> => {
  return fetchWithNetworkError(path, {
    ...options,
    headers: buildHeaders(options, requestCsrfToken),
  });
};

const isJsonMediaType = (contentType: string): boolean => {
  const mediaType = contentType.split(";", 1)[0]?.trim().toLowerCase() ?? "";
  return mediaType === "application/json" || mediaType.endsWith("+json");
};

const isReplayableRequest = (options: RequestInit): boolean => {
  if (options.body == null) {
    return true;
  }

  if (typeof options.body !== "string") {
    return false;
  }

  const contentType = new Headers(options.headers).get("Content-Type");

  // String bodies are JSON by default in this client. Explicit non-JSON
  // strings are not part of OfferTrack's replayable request contract.
  return contentType == null || isJsonMediaType(contentType);
};

export const request = async <T,>(
  path: string,
  schema: z.ZodType<T>,
  options: RequestInit = {},
): Promise<T> => {
  const method = (options.method ?? "GET").toUpperCase();
  const isUnsafe = unsafeMethods.has(method);
  const requestCsrfToken = isUnsafe ? await fetchCsrfToken() : undefined;
  let response = await performRequest(path, options, requestCsrfToken);
  let text = await response.text();

  if (!response.ok && isUnsafe) {
    const error = new ApiError(response.status, text);

    if (getApiErrorCode(error) === CSRF_INVALID_ERROR_CODE) {
      // A parallel request may already be refreshing this token. Only the
      // request that still owns the stale cached value invalidates state.
      clearCsrfTokenIfCurrent(requestCsrfToken);

      if (isReplayableRequest(options)) {
        const refreshedToken = await fetchCsrfToken();
        response = await performRequest(path, options, refreshedToken);
        text = await response.text();
      }
    }
  }

  return readSuccessfulJson(response, text, schema);
};
