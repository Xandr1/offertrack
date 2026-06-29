import { z } from "zod";
import {
  applicationSchema,
  applicationDraftInterviewSchema,
  applicationDraftResponseSchema,
  applicationWithInterviewsSchema,
  applicationInterviewSchema,
  applicationInterviewsSchema,
  applicationsPageSchema,
  applicationBoardColumnSchema,
  applicationBoardSchema,
  applicationStageSchema,
  applicationsSchema,
  authResponseSchema,
  genericSuccessResponseSchema,
  registerResponseSchema,
  dashboardApplicationItemSchema,
  dashboardInterviewItemSchema,
  dashboardSummarySchema,
  interviewStatusSchema,
  interviewTypeSchema,
  nextInterviewSchema,
  settingsSchema,
  userSummarySchema,
  verifyEmailResponseSchema,
  workModeSchema,
} from "./schemas";

export type UserSummary = z.infer<typeof userSummarySchema>;
export type AuthResponse = z.infer<typeof authResponseSchema>;
export type RegisterResponse = z.infer<typeof registerResponseSchema>;
export type VerifyEmailResponse = z.infer<typeof verifyEmailResponseSchema>;
export type GenericSuccessResponse = z.infer<typeof genericSuccessResponseSchema>;
export type ApplicationStage = z.infer<typeof applicationStageSchema>;
export type WorkMode = z.infer<typeof workModeSchema>;
export type InterviewType = z.infer<typeof interviewTypeSchema>;
export type InterviewStatus = z.infer<typeof interviewStatusSchema>;
export type NextInterview = z.infer<typeof nextInterviewSchema>;
export type Application = z.infer<typeof applicationSchema>;
export type ApplicationDraftInterview = z.infer<
  typeof applicationDraftInterviewSchema
>;
export type ApplicationDraftResponse = z.infer<
  typeof applicationDraftResponseSchema
>;
export type ApplicationWithInterviews = z.infer<
  typeof applicationWithInterviewsSchema
>;
export type Applications = z.infer<typeof applicationsSchema>;
export type ApplicationsPage = z.infer<typeof applicationsPageSchema>;
export type ApplicationBoardColumn = z.infer<
  typeof applicationBoardColumnSchema
>;
export type ApplicationBoard = z.infer<typeof applicationBoardSchema>;
export type ApplicationInterview = z.infer<typeof applicationInterviewSchema>;
export type ApplicationInterviews = z.infer<typeof applicationInterviewsSchema>;
export type DashboardApplicationItem = z.infer<
  typeof dashboardApplicationItemSchema
>;
export type DashboardInterviewItem = z.infer<typeof dashboardInterviewItemSchema>;
export type DashboardSummary = z.infer<typeof dashboardSummarySchema>;
export type Settings = z.infer<typeof settingsSchema>;

export type RegisterRequest = {
  name?: string;
  email: string;
  password: string;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type VerifyEmailRequest = {
  token: string;
};

export type ResendVerificationRequest = {
  email: string;
};

export type ForgotPasswordRequest = {
  email: string;
};

export type ResetPasswordRequest = {
  token: string;
  newPassword: string;
};

export type CreateApplicationRequest = {
  companyName: string;
  positionTitle: string;
  jobUrl?: string | null;
  stage?: ApplicationStage;
  location?: string | null;
  workMode?: WorkMode | null;
  appliedAt?: string | null;
  notes?: string | null;
  interviews?: CreateApplicationInterviewItemRequest[];
};

export type ApplicationDraftRequest = {
  jobUrl: string;
};

export type ReplaceApplicationRequest = {
  companyName: string;
  positionTitle: string;
  jobUrl: string | null;
  stage: ApplicationStage;
  location: string | null;
  workMode: WorkMode | null;
  appliedAt: string | null;
  notes: string | null;
  interviews: ReplaceApplicationInterviewItemRequest[];
};

export type UpdateApplicationStageRequest = {
  stage: ApplicationStage;
};

export type ApplicationSortField = "updatedAt" | "createdAt";

export type SortDirection = "asc" | "desc";

export type ApplicationsListParams = {
  page: number;
  size: number;
  search: string;
  stage: ApplicationStage | null;
  sort: ApplicationSortField;
  direction: SortDirection;
};

export type ApplicationsBoardParams = {
  search: string;
  sort: ApplicationSortField;
  direction: SortDirection;
};

export type ApplicationBoardColumnParams = {
  stage: ApplicationStage;
  search: string;
  sort: ApplicationSortField;
  direction: SortDirection;
  offset: number;
};

export type CreateApplicationInterviewItemRequest = {
  type: InterviewType;
  status?: InterviewStatus;
  scheduledAt?: string | null;
};

export type ReplaceApplicationInterviewItemRequest = {
  id?: string;
  type: InterviewType;
  status: InterviewStatus;
  scheduledAt: string | null;
};

export type UpdateInterviewStatusRequest = {
  status: InterviewStatus;
};

export type UpdateSettingsRequest = {
  followUpAfterApplyingDays: number;
  upcomingInterviewDays: number;
  followUpAfterInterviewDays: number;
  targetRole?: string | null;
};
