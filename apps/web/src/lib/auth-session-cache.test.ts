import { QueryClient } from "@tanstack/react-query";
import {
  clearAuthSessionQueries,
  clearProtectedDataQueries,
} from "./auth-session-cache";
import { queryKeys } from "./query-keys";

describe("auth-session-cache", () => {
  it("clears protected user data without removing the verified user", () => {
    const queryClient = seededClient();

    clearProtectedDataQueries(queryClient);

    expect(queryClient.getQueryData(queryKeys.authMe)).toBeDefined();
    expect(queryClient.getQueryData(queryKeys.applications.list())).toBeUndefined();
    expect(queryClient.getQueryData(queryKeys.dashboardSummary)).toBeUndefined();
    expect(queryClient.getQueryData(queryKeys.settings)).toBeUndefined();
    expect(queryClient.getQueryData(["public", "content"])).toBeDefined();
  });

  it("also clears the current user for a complete session cleanup", () => {
    const queryClient = seededClient();

    clearAuthSessionQueries(queryClient);

    expect(queryClient.getQueryData(queryKeys.authMe)).toBeUndefined();
    expect(queryClient.getQueryData(queryKeys.applications.list())).toBeUndefined();
    expect(queryClient.getQueryData(["public", "content"])).toBeDefined();
  });
});

const seededClient = () => {
  const queryClient = new QueryClient();
  queryClient.setQueryData(queryKeys.authMe, { id: "user-1" });
  queryClient.setQueryData(queryKeys.applications.list(), { stale: true });
  queryClient.setQueryData(queryKeys.dashboardSummary, { stale: true });
  queryClient.setQueryData(queryKeys.settings, { stale: true });
  queryClient.setQueryData(["public", "content"], { retained: true });
  return queryClient;
};
