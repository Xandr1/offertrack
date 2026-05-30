import { z } from "zod";
import { request } from "./client";
import { authResponseSchema, userSummarySchema } from "./schemas";
import { AuthResponse, LoginRequest, RegisterRequest, UserSummary } from "./types";

export const register = (payload: RegisterRequest): Promise<AuthResponse> => {
  return request<AuthResponse>("/auth/register", authResponseSchema, {
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

export const getCurrentUser = (): Promise<UserSummary> => {
  return request<UserSummary>("/api/me", userSummarySchema);
};
