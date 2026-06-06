import { z } from "zod";
import {
  applicationSchema,
  applicationWithInterviewsSchema,
  applicationInterviewSchema,
  applicationInterviewsSchema,
  applicationStageSchema,
  applicationsSchema,
  authResponseSchema,
  dashboardApplicationItemSchema,
  dashboardInterviewItemSchema,
  dashboardSummarySchema,
  interviewStatusSchema,
  interviewTypeSchema,
  nextInterviewSchema,
  userSummarySchema,
  workModeSchema,
} from "./schemas";

export type UserSummary = z.infer<typeof userSummarySchema>;
export type AuthResponse = z.infer<typeof authResponseSchema>;
export type ApplicationStage = z.infer<typeof applicationStageSchema>;
export type WorkMode = z.infer<typeof workModeSchema>;
export type InterviewType = z.infer<typeof interviewTypeSchema>;
export type InterviewStatus = z.infer<typeof interviewStatusSchema>;
export type NextInterview = z.infer<typeof nextInterviewSchema>;
export type Application = z.infer<typeof applicationSchema>;
export type ApplicationWithInterviews = z.infer<
  typeof applicationWithInterviewsSchema
>;
export type Applications = z.infer<typeof applicationsSchema>;
export type ApplicationInterview = z.infer<typeof applicationInterviewSchema>;
export type ApplicationInterviews = z.infer<typeof applicationInterviewsSchema>;
export type DashboardApplicationItem = z.infer<
  typeof dashboardApplicationItemSchema
>;
export type DashboardInterviewItem = z.infer<typeof dashboardInterviewItemSchema>;
export type DashboardSummary = z.infer<typeof dashboardSummarySchema>;

export type RegisterRequest = {
  name?: string;
  email: string;
  password: string;
};

export type LoginRequest = {
  email: string;
  password: string;
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
