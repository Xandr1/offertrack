/** @jest-environment jsdom */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { getCurrentUser } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { useFreshAuthSession } from "./use-fresh-auth-session";

jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"),
  getCurrentUser: jest.fn(),
}));

const mockedGetCurrentUser = jest.mocked(getCurrentUser);
const user = { id: "user-1", email: "person@example.com", name: null };

describe("useFreshAuthSession", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("reports a background refresh as authenticated and retains verified data after a transient failure", async () => {
    const initialSession = createDeferred<typeof user>();
    const backgroundSession = createDeferred<typeof user>();
    mockedGetCurrentUser
      .mockReturnValueOnce(initialSession.promise)
      .mockReturnValueOnce(backgroundSession.promise);
    const queryClient = createQueryClient();
    const { result } = renderHook(() => useFreshAuthSession(), {
      wrapper: createWrapper(queryClient),
    });

    expect(result.current.status).toBe("verifying");
    initialSession.resolve(user);
    await waitFor(() => expect(result.current.status).toBe("authenticated"));
    expect(result.current).toEqual(
      expect.objectContaining({
        isRefreshing: false,
        status: "authenticated",
        user,
      }),
    );

    act(() => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.authMe });
    });
    await waitFor(() =>
      expect(result.current).toEqual(
        expect.objectContaining({
          isRefreshing: true,
          status: "authenticated",
          user,
        }),
      ),
    );

    backgroundSession.reject(new Error("temporary network failure"));
    await waitFor(() =>
      expect(queryClient.getQueryState(queryKeys.authMe)?.status).toBe("error"),
    );
    expect(result.current).toEqual(
      expect.objectContaining({
        isRefreshing: false,
        status: "authenticated",
        user,
      }),
    );
  });

  it("does not trust cached user data when the initial verification fails", async () => {
    const initialSession = createDeferred<typeof user>();
    mockedGetCurrentUser.mockReturnValue(initialSession.promise);
    const queryClient = createQueryClient();
    queryClient.setQueryData(queryKeys.authMe, user);
    const { result } = renderHook(() => useFreshAuthSession(), {
      wrapper: createWrapper(queryClient),
    });

    expect(result.current.status).toBe("verifying");
    initialSession.reject(new Error("initial verification failed"));

    await waitFor(() => expect(result.current.status).toBe("error"));
  });
});

const createQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

const createWrapper = (queryClient: QueryClient) => {
  const QueryClientWrapper = ({
    children,
  }: {
    children: React.ReactNode;
  }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  QueryClientWrapper.displayName = "QueryClientWrapper";

  return QueryClientWrapper;
};

const createDeferred = <Value,>() => {
  let resolve: (value: Value) => void = () => undefined;
  let reject: (error: unknown) => void = () => undefined;
  const promise = new Promise<Value>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });

  return { promise, reject, resolve };
};
