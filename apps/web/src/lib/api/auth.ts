import { z } from "zod";
import { API_URL, clearCsrfToken, ensureFreshAuthentication, request } from "./client";
import { authState, AuthCoordinationError, emitAuthEvent, withAuthLock } from "../auth-coordinator";
import {
  authResponseSchema,
  genericSuccessResponseSchema,
  registerResponseSchema,
  userSummarySchema,
  verifyEmailResponseSchema,
} from "./schemas";
import {
  AuthResponse,
  GenericSuccessResponse,
  ForgotPasswordRequest,
  LoginRequest,
  RegisterRequest,
  RegisterResponse,
  ResendVerificationRequest,
  ResetPasswordRequest,
  UserSummary,
  VerifyEmailRequest,
  VerifyEmailResponse,
} from "./types";

export const register = (payload: RegisterRequest): Promise<RegisterResponse> => {
  return request<RegisterResponse>("/auth/register", registerResponseSchema, {
    method: "POST",
    body: JSON.stringify(payload),
  });
};

export const login = async (payload: LoginRequest): Promise<AuthResponse> => {
  return withAuthLock(async () => {
  const response = await request<AuthResponse>(
    "/auth/login",
    authResponseSchema,
    {
      method: "POST",
      body: JSON.stringify(payload),
    },
  );

  clearCsrfToken();
  emitAuthEvent("signed-in");
  return response;
  });
};

export const getGoogleLoginUrl = (): string => {
  return `${API_URL}/auth/oauth2/google/start`;
};

export const logout = async (): Promise<void> => {
  return withAuthLock(async () => {
  await request<void>("/auth/logout", z.undefined(), {
    method: "POST",
  });

  clearCsrfToken();
  emitAuthEvent("signed-out");
  });
};

export const logoutAll = async (): Promise<void> => {
  // Refresh takes the lifecycle lock itself; never nest Web Locks.
  const generation = authState().generation;
  await ensureFreshAuthentication();
  return withAuthLock(async () => {
    if (authState().generation !== generation) throw new AuthCoordinationError();
    await request<void>("/auth/logout-all", z.undefined(), { method: "POST" });
    clearCsrfToken();
    emitAuthEvent("signed-out");
  });
};

export const verifyEmail = (
  payload: VerifyEmailRequest,
): Promise<VerifyEmailResponse> => {
  return request<VerifyEmailResponse>("/auth/email/verify", verifyEmailResponseSchema, {
    method: "POST",
    body: JSON.stringify(payload),
  });
};

export const resendVerificationEmail = (
  payload: ResendVerificationRequest,
): Promise<GenericSuccessResponse> => {
  return request<GenericSuccessResponse>(
    "/auth/email/verification/resend",
    genericSuccessResponseSchema,
    {
      method: "POST",
      body: JSON.stringify(payload),
    },
  );
};

export const forgotPassword = (
  payload: ForgotPasswordRequest,
): Promise<GenericSuccessResponse> => {
  return request<GenericSuccessResponse>(
    "/auth/password/forgot",
    genericSuccessResponseSchema,
    {
      method: "POST",
      body: JSON.stringify(payload),
    },
  );
};

export const resetPassword = (
  payload: ResetPasswordRequest,
): Promise<GenericSuccessResponse> => {
  return request<GenericSuccessResponse>(
    "/auth/password/reset",
    genericSuccessResponseSchema,
    {
      method: "POST",
      body: JSON.stringify(payload),
    },
  );
};

export const getCurrentUser = (allowRefresh = true): Promise<UserSummary> => {
  return request<UserSummary>("/api/me", userSummarySchema, {}, allowRefresh);
};
