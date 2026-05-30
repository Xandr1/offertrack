import { ApiError, getCurrentUser, getErrorMessage } from "@/lib/api";

type ResolvedRequestError = {
  message: string;
  shouldRedirectToLogin: boolean;
};

export const isAuthError = (error: unknown): error is ApiError => {
  return (
    error instanceof ApiError && (error.status === 401 || error.status === 403)
  );
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
): Promise<boolean> => {
  const shouldRedirect = await shouldRedirectToLoginAfterError(requestError);
  if (!shouldRedirect) {
    return false;
  }

  router.replace("/login");
  return true;
};
