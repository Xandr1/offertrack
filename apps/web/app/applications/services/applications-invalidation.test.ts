import { QueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query-keys";
import { invalidateApplicationsFeatureQueries } from "./applications-invalidation";

describe("applications invalidation", () => {
  it("suppresses active refetch only for board queries", () => {
    const queryClient = new QueryClient();
    const invalidateQueries = jest.spyOn(queryClient, "invalidateQueries");

    invalidateApplicationsFeatureQueries(queryClient, {
      applicationId: "app-1",
      refetchInterviews: false,
    });

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.list(),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.board(),
      refetchType: "none",
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.dashboardSummary,
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.detail("app-1"),
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.applications.interviews("app-1"),
      refetchType: "none",
    });
  });
});
