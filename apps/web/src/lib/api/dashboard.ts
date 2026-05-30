import { request } from "./client";
import { dashboardSummarySchema } from "./schemas";
import { DashboardSummary } from "./types";

export const getDashboardSummary = (): Promise<DashboardSummary> => {
  return request<DashboardSummary>("/api/dashboard/summary", dashboardSummarySchema);
};
