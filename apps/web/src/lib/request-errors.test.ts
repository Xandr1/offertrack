import { ApiError, NetworkError } from "@/lib/api";
import {
  getRequestErrorMessage,
  isAuthError,
  isEmailNotVerifiedError,
  redirectToLoginIfProtectedRoute,
  resolveRequestError,
  shouldRedirectToLoginAfterError,
} from "./request-errors";

describe("request-errors", () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it("detects auth errors", () => {
    expect(isAuthError(new ApiError(401, ""))).toBe(true);
    expect(isAuthError(new ApiError(403, ""))).toBe(true);
    expect(
      isAuthError(
        new ApiError(403, JSON.stringify({ code: "EMAIL_NOT_VERIFIED" })),
      ),
    ).toBe(false);
    expect(isAuthError(new ApiError(500, ""))).toBe(false);
    expect(isAuthError(new Error("boom"))).toBe(false);
  });

  it("detects email verification errors", () => {
    const error = new ApiError(
      403,
      JSON.stringify({ code: "EMAIL_NOT_VERIFIED" }),
    );

    expect(isEmailNotVerifiedError(error)).toBe(true);
    expect(getRequestErrorMessage(error)).toBe(
      "Please verify your email before signing in.",
    );
  });

  it("resolves redirect for 401 and not for non-api errors", async () => {
    await expect(
      shouldRedirectToLoginAfterError(new ApiError(401, "")),
    ).resolves.toBe(true);
    await expect(
      shouldRedirectToLoginAfterError(new NetworkError()),
    ).resolves.toBe(false);
  });

  it("checks current user for 403 responses", async () => {
    const fetchSpy = jest.spyOn(global, "fetch");

    fetchSpy.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          email: "test@example.com",
          id: "user-1",
          name: null,
        }),
        { status: 200 },
      ),
    );

    await expect(
      shouldRedirectToLoginAfterError(new ApiError(403, "")),
    ).resolves.toBe(false);

    fetchSpy.mockResolvedValueOnce(new Response("Unauthorized", { status: 401 }));

    await expect(
      shouldRedirectToLoginAfterError(new ApiError(403, "")),
    ).resolves.toBe(true);
  });

  it("does not redirect for email verification errors", async () => {
    await expect(
      shouldRedirectToLoginAfterError(
        new ApiError(403, JSON.stringify({ code: "EMAIL_NOT_VERIFIED" })),
      ),
    ).resolves.toBe(false);
  });

  it("resolves message and redirect decision in one step", async () => {
    const resolvedError = await resolveRequestError(new ApiError(401, ""));

    expect(resolvedError.shouldRedirectToLogin).toBe(true);
    expect(resolvedError.message).toBe("Please sign in again.");
  });

  it("returns user-facing request error messages", () => {
    expect(getRequestErrorMessage(new ApiError(401, ""))).toBe(
      "Please sign in again.",
    );
    expect(getRequestErrorMessage(new NetworkError())).toContain(
      "Cannot connect to the server",
    );
  });

  it("redirects to login for protected-route auth errors", async () => {
    const router = { replace: jest.fn() };

    await expect(
      redirectToLoginIfProtectedRoute(new ApiError(401, ""), router),
    ).resolves.toBe(true);
    expect(router.replace).toHaveBeenCalledWith("/login");
  });
});
