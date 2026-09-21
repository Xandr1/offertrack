import { z } from "zod";
import { authState, emitAuthEvent, withAuthLock, subscribeAuthEvents } from "../auth-coordinator";
import { getPublicApiUrl } from "../web-environment";
import {
  ApiError,
  getApiErrorCode,
  NetworkError,
  ResponseValidationError,
} from "./errors";

export const API_URL = getPublicApiUrl();

const CSRF_INVALID_ERROR_CODE = "CSRF_INVALID";
const AUTHENTICATION_REQUIRED = "AUTHENTICATION_REQUIRED";
let refreshRequest: Promise<void> | null = null;
let observedUserId: string | null = null;
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

export const requiresAuthentication = (error: unknown): error is ApiError =>
  error instanceof ApiError && error.status === 401 &&
  getApiErrorCode(error) === AUTHENTICATION_REQUIRED;

const authenticationRequired = (): ApiError =>
  new ApiError(401, JSON.stringify({ status: 401, code: AUTHENTICATION_REQUIRED,
    message: "Please sign in again.", path: "/auth/refresh", timestamp: new Date().toISOString() }));

subscribeAuthEvents(event => {
  if (event !== "refreshed") {
    clearCsrfToken();
    if (event === "signed-out") observedUserId = null;
  }
});

const currentUserSchema = z.object({ id: z.string() });

export const probeAuthentication = async (): Promise<string> => {
  const response = await fetchWithNetworkError("/api/me", {
    method: "GET", cache: "no-store", headers: new Headers({ Accept: "application/json" }),
  });
  return readSuccessfulJson(response, await response.text(), currentUserSchema).id;
};

const refreshAuthentication = (expectedGeneration: number, expectedUser: string | null): Promise<void> => {
  if (authState().signedOut || authState().generation !== expectedGeneration) return Promise.reject(authenticationRequired());
  if (refreshRequest) return refreshRequest;
  const pending = withAuthLock(async () => {
    if (authState().signedOut || authState().generation !== expectedGeneration) throw authenticationRequired();
    let userId: string;
    try {
      userId = await probeAuthentication();
    } catch (error) {
      if (!requiresAuthentication(error)) throw error;
      clearCsrfToken();
      const token = await fetchCsrfToken();
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 10_000);
      try {
        // Exactly one rotation attempt; never run this through the request interceptor.
        const response = await performRequest("/auth/refresh",
          { method: "POST", signal: controller.signal }, token);
        const text = await response.text();
        if (response.status !== 204) throw new ApiError(response.status, text);
      } finally { clearTimeout(timeout); }
      userId = await probeAuthentication();
      emitAuthEvent("refreshed");
    }
    if (authState().generation !== expectedGeneration) throw authenticationRequired();
    if (expectedUser && userId !== expectedUser) {
      observedUserId = userId;
      emitAuthEvent("signed-in");
      throw authenticationRequired();
    }
    observedUserId = userId;
  }, true).catch(error => {
    // A concurrent account change already owns the new state; never log that account out.
    if (authState().generation === expectedGeneration) emitAuthEvent("signed-out");
    throw error;
  });
  refreshRequest = pending;
  void pending.then(() => { if (refreshRequest === pending) refreshRequest = null; },
    () => { if (refreshRequest === pending) refreshRequest = null; });
  return pending;
};

/** Used by logout-all: its request itself is excluded from automatic refresh. */
export const ensureFreshAuthentication = async (): Promise<void> => {
  try { observedUserId = await probeAuthentication(); }
  catch (error) {
    if (!requiresAuthentication(error)) throw error;
    await refreshAuthentication(authState().generation, observedUserId);
  }
};

export const request = async <T,>(
  path: string,
  schema: z.ZodType<T>,
  options: RequestInit = {},
  allowRefresh = true,
): Promise<T> => {
  const state = authState();
  const expectedUser = observedUserId;
  const protectedRequest = !path.startsWith("/auth/") && !path.startsWith("/oauth2/") && !path.startsWith("/login/oauth2/");
  if (protectedRequest && state.signedOut) throw authenticationRequired();
  let retried = false;
  const method = (options.method ?? "GET").toUpperCase();
  const isUnsafe = unsafeMethods.has(method);
  const requestCsrfToken = isUnsafe ? await fetchCsrfToken() : undefined;
  let response = await performRequest(path, options, requestCsrfToken);
  let text = await response.text();

  if (!response.ok && isUnsafe) {
    const error = new ApiError(response.status, text);

    if (error.status === 403 && getApiErrorCode(error) === CSRF_INVALID_ERROR_CODE) {
      // A parallel request may already be refreshing this token. Only the
      // request that still owns the stale cached value invalidates state.
      clearCsrfTokenIfCurrent(requestCsrfToken);

      if (isReplayableRequest(options)) {
        retried = true;
        const refreshedToken = await fetchCsrfToken();
        response = await performRequest(path, options, refreshedToken);
        text = await response.text();
      }
    }
  }

  if (allowRefresh && protectedRequest && !retried && isReplayableRequest(options) &&
      !response.ok && requiresAuthentication(new ApiError(response.status, text))) {
    await refreshAuthentication(state.generation, expectedUser);
    if (authState().generation !== state.generation) throw authenticationRequired();
    response = await performRequest(path, options, isUnsafe ? await fetchCsrfToken() : undefined);
    text = await response.text();
  }
  if (protectedRequest && authState().generation !== state.generation) throw authenticationRequired();
  const result = readSuccessfulJson(response, text, schema);
  if (path === "/api/me") {
    const current = currentUserSchema.safeParse(result);
    if (current.success) {
      const changed = observedUserId !== null && observedUserId !== current.data.id;
      observedUserId = current.data.id;
      if (changed) emitAuthEvent("signed-in");
    }
  }
  return result;
};
