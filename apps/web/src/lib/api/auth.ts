import { z } from "zod";
import { API_URL, request } from "./client";
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

export const login = (payload: LoginRequest): Promise<AuthResponse> => {
  return request<AuthResponse>("/auth/login", authResponseSchema, {
    method: "POST",
    body: JSON.stringify(payload),
  });
};

export const getGoogleLoginUrl = (): string => {
  return `${API_URL}/auth/oauth2/google/start`;
};

export const logout = (): Promise<void> => {
  return request<void>("/auth/logout", z.undefined(), {
    method: "POST",
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

export const getCurrentUser = (): Promise<UserSummary> => {
  return request<UserSummary>("/api/me", userSummarySchema);
};
