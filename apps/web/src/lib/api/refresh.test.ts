import { z } from "zod";
import { authState, emitAuthEvent } from "../auth-coordinator";
import { ApiError } from "./errors";
import { request } from "./client";

const schema = z.object({ ok: z.boolean() });
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const required = () => json({ code: "AUTHENTICATION_REQUIRED" }, 401);
const fetchMock = jest.fn<Promise<Response>, Parameters<typeof fetch>>();

beforeEach(() => {
  fetchMock.mockReset();
  global.fetch = fetchMock;
  emitAuthEvent("signed-in");
});

it.each([
  [401, "Unauthorized"], [401, "{}"], [401, '{"code":"INVALID_CREDENTIALS"}'],
  [403, '{"code":"AUTHENTICATION_REQUIRED"}'], [429, "{}"], [503, "{}"],
])("never refreshes status %s outside the explicit contract", async (status, body) => {
  fetchMock.mockResolvedValueOnce(new Response(body, { status }));
  await expect(request("/api/example", schema)).rejects.toBeInstanceOf(ApiError);
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it("coalesces simultaneous failures and retries each request once after confirmed rotation", async () => {
  let refreshed = false;
  fetchMock.mockImplementation(async input => {
    const path = new URL(String(input)).pathname;
    if (path === "/auth/csrf") return json({ token: "masked", headerName: "X-XSRF-TOKEN" });
    if (path === "/auth/refresh") { refreshed = true; return new Response(null, { status: 204 }); }
    if (path === "/api/me" && refreshed) return json({ id: "current-user" });
    return refreshed ? json({ ok: true }) : required();
  });
  await expect(Promise.all([request("/api/a", schema), request("/api/b", schema)]))
    .resolves.toEqual([{ ok: true }, { ok: true }]);
  const paths = fetchMock.mock.calls.map(([url]) => new URL(String(url)).pathname);
  expect(paths.filter(path => path === "/auth/refresh")).toHaveLength(1);
  expect(paths.filter(path => path === "/api/a")).toHaveLength(2);
  expect(paths.filter(path => path === "/api/b")).toHaveLength(2);
  expect(fetchMock.mock.calls.every(([, options]) => options?.credentials === "include")).toBe(true);
});

it("does not rotate after a probe with an unrelated 401", async () => {
  fetchMock.mockResolvedValueOnce(required()).mockResolvedValueOnce(json({ code: "OTHER" }, 401));
  await expect(request("/api/a", schema)).rejects.toBeInstanceOf(ApiError);
  expect(fetchMock).toHaveBeenCalledTimes(2);
  expect(authState().signedOut).toBe(true);
});

it("never retries an ambiguous refresh and discards later protected work", async () => {
  fetchMock.mockResolvedValueOnce(required()).mockResolvedValueOnce(required())
    .mockResolvedValueOnce(json({ token: "masked", headerName: "X-XSRF-TOKEN" }))
    .mockRejectedValueOnce(new TypeError("Network failure"));
  await expect(request("/api/a", schema)).rejects.toThrow();
  await expect(request("/api/b", schema)).rejects.toThrow();
  expect(fetchMock).toHaveBeenCalledTimes(4);
  expect(authState().signedOut).toBe(true);
});

it("does not refresh again when the single replay fails", async () => {
  fetchMock.mockResolvedValueOnce(required()).mockResolvedValueOnce(required())
    .mockResolvedValueOnce(json({ token: "masked", headerName: "X-XSRF-TOKEN" }))
    .mockResolvedValueOnce(new Response(null, { status: 204 }))
    .mockResolvedValueOnce(json({ id: "current-user" })).mockResolvedValueOnce(required());
  await expect(request("/api/a", schema)).rejects.toBeInstanceOf(ApiError);
  expect(fetchMock).toHaveBeenCalledTimes(6);
});

it("shares the one replay budget with CSRF recovery", async () => {
  fetchMock.mockResolvedValueOnce(json({ token: "old", headerName: "X-XSRF-TOKEN" }))
    .mockResolvedValueOnce(json({ code: "CSRF_INVALID" }, 403))
    .mockResolvedValueOnce(json({ token: "new", headerName: "X-XSRF-TOKEN" }))
    .mockResolvedValueOnce(required());
  await expect(request("/api/a", schema, { method: "POST", body: "{}" })).rejects.toBeInstanceOf(ApiError);
  expect(fetchMock).toHaveBeenCalledTimes(4);
});

it.each(["login", "register", "refresh", "logout", "logout-all", "csrf", "email/verify", "password/reset", "password/forgot"])
("excludes /auth/%s from automatic refresh", async operation => {
  fetchMock.mockResolvedValueOnce(required());
  await expect(request(`/auth/${operation}`, schema)).rejects.toBeInstanceOf(ApiError);
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it("discards a late response after another account signs in", async () => {
  let finish!: (response: Response) => void;
  fetchMock.mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
  const pending = request("/api/a", schema);
  emitAuthEvent("signed-in");
  finish(json({ ok: true }));
  await expect(pending).rejects.toBeInstanceOf(ApiError);
  expect(fetchMock).toHaveBeenCalledTimes(1);
});
