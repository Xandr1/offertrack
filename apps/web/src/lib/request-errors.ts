import {
  ApiError,
  EMAIL_NOT_VERIFIED_ERROR_CODE,
  getCurrentUser,
  getErrorMessage,
  hasApiErrorCode,
} from "@/lib/api";
import type { QueryClient } from "@tanstack/react-query";
import { clearAuthSessionQueries } from "@/lib/auth-session-cache";

type ResolvedRequestError = {
  message: string;
  shouldRedirectToLogin: boolean;
};

export const isAuthError = (error: unknown): error is ApiError => {
  return (
    error instanceof ApiError &&
    (error.status === 401 ||
      (error.status === 403 && !isEmailNotVerifiedError(error)))
  );
};

export const isEmailNotVerifiedError = (error: unknown): boolean => {
  return hasApiErrorCode(error, EMAIL_NOT_VERIFIED_ERROR_CODE);
};

export const getRequestErrorMessage = (error: unknown): string => {
  return getErrorMessage(error);
};

export const shouldRedirectToLoginAfterError = async (
  requestError: unknown,
): Promise<boolean> => {
  if (!(requestError instanceof ApiError)) {
    return false;
  }

  if (requestError.status === 401) {
    return true;
  }

  if (isEmailNotVerifiedError(requestError)) {
    return false;
  }

  if (requestError.status !== 403) {
    return false;
  }

  try {
    await getCurrentUser();
    return false;
  } catch (currentUserError) {
    return isAuthError(currentUserError);
  }
};

export const resolveRequestError = async (
  requestError: unknown,
): Promise<ResolvedRequestError> => {
  return {
    message: getRequestErrorMessage(requestError),
    shouldRedirectToLogin: await shouldRedirectToLoginAfterError(requestError),
  };
};

type RouterLike = {
  replace: (href: string) => void;
};

export const redirectToLoginIfProtectedRoute = async (
  requestError: unknown,
  router: RouterLike,
  queryClient: QueryClient,
): Promise<boolean> => {
  const shouldRedirect = await shouldRedirectToLoginAfterError(requestError);
  if (!shouldRedirect) {
    return false;
  }

  clearAuthSessionQueries(queryClient);
  router.replace("/login");
  return true;
};
