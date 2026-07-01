import { z } from "zod";

export const userSummarySchema = z.object({
  id: z.string(),
  email: z.string(),
  name: z.string().nullable(),
});

export const authResponseSchema = z.object({
  user: userSummarySchema,
});

export const registerResponseSchema = z.object({
  emailVerificationRequired: z.boolean(),
});

export const verifyEmailResponseSchema = z.object({
  verified: z.boolean(),
});

export const genericSuccessResponseSchema = z.object({
  ok: z.boolean(),
});

export const apiErrorResponseSchema = z.object({
  code: z.string(),
});

export const applicationStageSchema = z.preprocess((value) => {
  if (typeof value === "string") {
    return value.toLowerCase();
  }

  return value;
}, z.enum([
  "initial",
  "applied",
  "interviewing",
  "offer",
  "rejected",
]));

export const workModeSchema = z.preprocess((value) => {
  if (typeof value === "string") {
    return value.toLowerCase();
  }

  return value;
}, z.enum(["remote", "hybrid", "onsite"]));

export const interviewTypeSchema = z.preprocess((value) => {
  if (typeof value === "string") {
    return value.toLowerCase();
  }

  return value;
}, z.enum([
  "recruiter",
  "hr",
  "technical",
  "hiring_manager",
  "team_match",
  "home_assignment",
  "behavioral",
  "other",
]));

export const interviewStatusSchema = z.preprocess((value) => {
  if (typeof value === "string") {
    return value.toLowerCase();
  }

  return value;
}, z.enum(["initial", "scheduled", "passed", "rejected"]));

const optionalNullableStringSchema = z.preprocess(
  (value) => (value === undefined ? null : value),
  z.string().nullable(),
);

export const nextInterviewSchema = z.object({
  id: z.string(),
  type: interviewTypeSchema,
  status: interviewStatusSchema,
  scheduledAt: optionalNullableStringSchema,
});

export const applicationSchema = z.object({
  id: z.string(),
  companyName: z.string(),
  positionTitle: z.string(),
  jobUrl: optionalNullableStringSchema,
  stage: applicationStageSchema,
  location: optionalNullableStringSchema,
  workMode: z.preprocess(
    (value) => (value === undefined ? null : value),
    workModeSchema.nullable(),
  ),
  appliedAt: optionalNullableStringSchema,
  followedUpAt: optionalNullableStringSchema,
  notes: optionalNullableStringSchema,
  createdAt: z.string(),
  updatedAt: z.string(),
  nextInterview: z.preprocess(
    (value) => (value === undefined ? null : value),
    nextInterviewSchema.nullable(),
  ),
  lastInterview: z.preprocess(
    (value) => (value === undefined ? null : value),
    nextInterviewSchema.nullable(),
  ),
});

export const applicationsSchema = z.array(applicationSchema);

export const applicationsPageSchema = z.object({
  items: applicationsSchema,
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalItems: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
});

export const applicationBoardColumnSchema = z.object({
  stage: applicationStageSchema,
  totalCount: z.number().int().nonnegative(),
  items: applicationsSchema,
  nextOffset: z.number().int().nonnegative(),
  hasMore: z.boolean(),
});

export const applicationBoardSchema = z.object({
  columns: z.array(applicationBoardColumnSchema),
});

export const applicationInterviewSchema = z.object({
  id: z.string(),
  applicationId: z.string(),
  type: interviewTypeSchema,
  status: interviewStatusSchema,
  scheduledAt: optionalNullableStringSchema,
  followedUpAt: optionalNullableStringSchema,
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const applicationInterviewsSchema = z.array(applicationInterviewSchema);

export const applicationWithInterviewsSchema = z.object({
  application: applicationSchema,
  interviews: applicationInterviewsSchema,
});

export const applicationDraftInterviewSchema = z.object({
  type: interviewTypeSchema,
  status: z.literal("initial"),
  scheduledAt: z.preprocess((value) => (value === undefined ? null : value), z.null()),
});

export const applicationDraftResponseSchema = z.object({
  companyName: optionalNullableStringSchema,
  positionTitle: optionalNullableStringSchema,
  jobUrl: z.string(),
  location: optionalNullableStringSchema,
  workMode: z.preprocess(
    (value) => (value === undefined ? null : value),
    workModeSchema.nullable(),
  ),
  stage: z.literal("initial"),
  notes: optionalNullableStringSchema,
  interviews: z.array(applicationDraftInterviewSchema),
  warnings: z.array(z.string()),
});

export const dashboardApplicationItemSchema = z.object({
  applicationId: z.string(),
  companyName: z.string(),
  positionTitle: z.string(),
  stage: applicationStageSchema,
  jobUrl: optionalNullableStringSchema,
  location: optionalNullableStringSchema,
  workMode: z.preprocess(
    (value) => (value === undefined ? null : value),
    workModeSchema.nullable(),
  ),
  appliedAt: optionalNullableStringSchema,
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const dashboardInterviewItemSchema = z.object({
  applicationId: z.string(),
  interviewId: z.string(),
  companyName: z.string(),
  positionTitle: z.string(),
  jobUrl: optionalNullableStringSchema,
  location: optionalNullableStringSchema,
  workMode: z.preprocess(
    (value) => (value === undefined ? null : value),
    workModeSchema.nullable(),
  ),
  scheduledAt: optionalNullableStringSchema,
  interviewType: interviewTypeSchema,
  status: interviewStatusSchema,
});

export const dashboardApplicationModulePageSchema = z.object({
  totalCount: z.number().int().nonnegative(),
  items: z.array(dashboardApplicationItemSchema),
  nextOffset: z.number().int().nonnegative(),
  hasMore: z.boolean(),
});

export const dashboardInterviewModulePageSchema = z.object({
  totalCount: z.number().int().nonnegative(),
  items: z.array(dashboardInterviewItemSchema),
  nextOffset: z.number().int().nonnegative(),
  hasMore: z.boolean(),
});

export const dashboardSummarySchema = z.object({
  activeProcesses: z.number().int().nonnegative(),
  needsAttention: z.number().int().nonnegative(),
  interviewing: z.number().int().nonnegative(),
  offers: z.number().int().nonnegative(),
  rejected: z.number().int().nonnegative(),
  followUpAfterApplyingDays: z.number().int().min(1).max(60),
  upcomingInterviewDays: z.number().int().min(1).max(60),
  followUpAfterInterviewDays: z.number().int().min(1).max(30),
  applicationsToFollowUp: dashboardApplicationModulePageSchema,
  upcomingInterviews: dashboardInterviewModulePageSchema,
  interviewsToFollowUp: dashboardInterviewModulePageSchema,
});

export const settingsSchema = z.object({
  followUpAfterApplyingDays: z.number().int().min(1).max(60),
  upcomingInterviewDays: z.number().int().min(1).max(60),
  followUpAfterInterviewDays: z.number().int().min(1).max(30),
  targetRole: z.preprocess(
    (value) => (value === undefined ? null : value),
    z.string().max(160).nullable(),
  ),
});
