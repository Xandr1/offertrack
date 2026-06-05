import { z } from "zod";

export const userSummarySchema = z.object({
  id: z.string(),
  email: z.string(),
  name: z.string().nullable(),
});

export const authResponseSchema = z.object({
  user: userSummarySchema,
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
}, z.enum(["planned", "scheduled", "completed", "passed", "rejected"]));

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
  notes: optionalNullableStringSchema,
  createdAt: z.string(),
  updatedAt: z.string(),
  nextInterview: z.preprocess(
    (value) => (value === undefined ? null : value),
    nextInterviewSchema.nullable(),
  ),
});

export const applicationsSchema = z.array(applicationSchema);

export const applicationInterviewSchema = z.object({
  id: z.string(),
  applicationId: z.string(),
  type: interviewTypeSchema,
  status: interviewStatusSchema,
  scheduledAt: optionalNullableStringSchema,
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const applicationInterviewsSchema = z.array(applicationInterviewSchema);

export const applicationWithInterviewsSchema = z.object({
  application: applicationSchema,
  interviews: applicationInterviewsSchema,
});

export const dashboardRecentApplicationSchema = z.object({
  id: z.string(),
  companyName: z.string(),
  positionTitle: z.string(),
  stage: applicationStageSchema,
  updatedAt: z.string(),
});

export const dashboardSummarySchema = z.object({
  activeProcesses: z.number().int().nonnegative(),
  needsAttention: z.number().int().nonnegative(),
  interviewing: z.number().int().nonnegative(),
  offers: z.number().int().nonnegative(),
  rejected: z.number().int().nonnegative(),
  recentApplications: z.array(dashboardRecentApplicationSchema),
});
