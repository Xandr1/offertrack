import { QueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/lib/query-keys";
import { invalidateSettingsFeatureQueries } from "./settings-invalidation";

describe("settings-invalidation", () => {
  it("invalidates settings and dashboard summary after save", () => {
    const queryClient = new QueryClient();
    const invalidateQueries = jest.spyOn(queryClient, "invalidateQueries");

    invalidateSettingsFeatureQueries(queryClient);

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.settings,
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: queryKeys.dashboardSummary,
    });
  });
});
