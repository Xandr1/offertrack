import { z } from "zod";
import { login, logout } from "./auth";
import { ApiError } from "./errors";
import { clearCsrfToken, request } from "./client";

const fetchMock = jest.fn<Promise<Response>, Parameters<typeof fetch>>();

const jsonResponse = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

const emptyResponse = (status = 204): Response => new Response(null, { status });

const requestHeaders = (callIndex: number): Headers => {
  const options = fetchMock.mock.calls[callIndex]?.[1];
  return new Headers(options?.headers);
};

describe("API client CSRF handling", () => {
  beforeAll(() => {
    Object.defineProperty(globalThis, "fetch", {
      configurable: true,
      value: fetchMock,
      writable: true,
    });
  });

  beforeEach(() => {
    fetchMock.mockReset();
    clearCsrfToken();
  });

  it("does not fetch or send a CSRF token for GET", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ ok: true }));

    await expect(
      request("/api/example", z.object({ ok: z.boolean() })),
    ).resolves.toEqual({ ok: true });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://localhost:8080/api/example",
    );
    expect(requestHeaders(0).has("X-XSRF-TOKEN")).toBe(false);
  });

  it("lazily obtains and sends the masked token for POST", async () => {
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ token: "masked-token", headerName: "X-XSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(jsonResponse({ ok: true }));

    await request("/api/example", z.object({ ok: z.boolean() }), {
      method: "POST",
      body: JSON.stringify({ value: 1 }),
    });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://localhost:8080/auth/csrf",
    );
    expect(requestHeaders(1).get("X-XSRF-TOKEN")).toBe("masked-token");
  });

  it("coalesces parallel token requests", async () => {
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ token: "shared-token", headerName: "X-XSRF-TOKEN" }),
      )
      .mockImplementation(() => Promise.resolve(jsonResponse({ ok: true })));

    await Promise.all([
      request("/api/one", z.object({ ok: z.boolean() }), { method: "POST" }),
      request("/api/two", z.object({ ok: z.boolean() }), { method: "POST" }),
    ]);

    const csrfCalls = fetchMock.mock.calls.filter(([url]) =>
      String(url).endsWith("/auth/csrf"),
    );
    expect(csrfCalls).toHaveLength(1);
    expect(requestHeaders(1).get("X-XSRF-TOKEN")).toBe("shared-token");
    expect(requestHeaders(2).get("X-XSRF-TOKEN")).toBe("shared-token");
  });

  it("refreshes and retries replayable JSON exactly once for CSRF_INVALID", async () => {
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ token: "stale-token", headerName: "X-XSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          { code: "CSRF_INVALID", message: "Security token is invalid." },
          403,
        ),
      )
      .mockResolvedValueOnce(
        jsonResponse({ token: "fresh-token", headerName: "X-XSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(jsonResponse({ ok: true }));

    await expect(
      request("/api/example", z.object({ ok: z.boolean() }), {
        method: "PATCH",
        body: JSON.stringify({ value: 1 }),
      }),
    ).resolves.toEqual({ ok: true });

    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(requestHeaders(1).get("X-XSRF-TOKEN")).toBe("stale-token");
    expect(requestHeaders(3).get("X-XSRF-TOKEN")).toBe("fresh-token");
  });

  it("coalesces a parallel CSRF_INVALID refresh", async () => {
    const invalidResponse = () =>
      jsonResponse(
        { code: "CSRF_INVALID", message: "Security token is invalid." },
        403,
      );

    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ token: "stale-token", headerName: "X-XSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(invalidResponse())
      .mockResolvedValueOnce(invalidResponse())
      .mockResolvedValueOnce(
        jsonResponse({ token: "fresh-token", headerName: "X-XSRF-TOKEN" }),
      )
      .mockImplementation(() => Promise.resolve(jsonResponse({ ok: true })));

    await Promise.all([
      request("/api/one", z.object({ ok: z.boolean() }), { method: "POST" }),
      request("/api/two", z.object({ ok: z.boolean() }), { method: "POST" }),
    ]);

    const csrfCalls = fetchMock.mock.calls.filter(([url]) =>
      String(url).endsWith("/auth/csrf"),
    );
    expect(csrfCalls).toHaveLength(2);
    expect(fetchMock).toHaveBeenCalledTimes(6);
    expect(requestHeaders(4).get("X-XSRF-TOKEN")).toBe("fresh-token");
    expect(requestHeaders(5).get("X-XSRF-TOKEN")).toBe("fresh-token");
  });

  it("does not retry an ordinary 403", async () => {
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ token: "masked-token", headerName: "X-XSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ code: "FORBIDDEN", message: "Forbidden" }, 403),
      );

    await expect(
      request("/api/example", z.unknown(), { method: "POST" }),
    ).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("clears stale state but does not replay a stream body", async () => {
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ token: "masked-token", headerName: "X-XSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          { code: "CSRF_INVALID", message: "Security token is invalid." },
          403,
        ),
      );

    await expect(
      request("/api/example", z.unknown(), {
        method: "POST",
        body: new ReadableStream(),
      }),
    ).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("does not replay an explicit non-JSON string body", async () => {
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ token: "masked-token", headerName: "X-XSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          { code: "CSRF_INVALID", message: "Security token is invalid." },
          403,
        ),
      );

    await expect(
      request("/api/example", z.unknown(), {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: "not-json",
      }),
    ).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("clears the in-memory token after successful login and logout", async () => {
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ token: "before-login", headerName: "X-XSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          user: {
            id: "00000000-0000-0000-0000-000000000001",
            email: "e2e@example.com",
            name: null,
          },
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ token: "before-logout", headerName: "X-XSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(emptyResponse());

    await login({ email: "e2e@example.com", password: "test-password" });
    await logout();

    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(requestHeaders(1).get("X-XSRF-TOKEN")).toBe("before-login");
    expect(requestHeaders(3).get("X-XSRF-TOKEN")).toBe("before-logout");
  });
});
