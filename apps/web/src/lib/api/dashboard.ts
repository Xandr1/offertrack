import { request } from "./client";
import {
  dashboardApplicationModulePageSchema,
  dashboardInterviewModulePageSchema,
  dashboardSummarySchema,
} from "./schemas";
import {
  DashboardApplicationModulePage,
  DashboardInterviewModulePage,
  DashboardSummary,
} from "./types";

export const getDashboardSummary = (): Promise<DashboardSummary> => {
  return request<DashboardSummary>("/api/dashboard/summary", dashboardSummarySchema);
};

export const getDashboardApplicationsToFollowUp = (
  offset: number,
): Promise<DashboardApplicationModulePage> =>
  request(
    `/api/dashboard/applications-to-follow-up?offset=${offset}`,
    dashboardApplicationModulePageSchema,
  );

export const getDashboardUpcomingInterviews = (
  offset: number,
): Promise<DashboardInterviewModulePage> =>
  request(
    `/api/dashboard/upcoming-interviews?offset=${offset}`,
    dashboardInterviewModulePageSchema,
  );

export const getDashboardInterviewsToFollowUp = (
  offset: number,
): Promise<DashboardInterviewModulePage> =>
  request(
    `/api/dashboard/interviews-to-follow-up?offset=${offset}`,
    dashboardInterviewModulePageSchema,
  );
