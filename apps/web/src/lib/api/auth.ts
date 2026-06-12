import { z } from "zod";
import { request } from "./client";
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
  LoginRequest,
  RegisterRequest,
  RegisterResponse,
  ResendVerificationRequest,
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

export const getCurrentUser = (): Promise<UserSummary> => {
  return request<UserSummary>("/api/me", userSummarySchema);
};
