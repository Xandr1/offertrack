import type {
  ApplicationSortField,
  ApplicationStage,
  InterviewStatus,
  InterviewType,
  SortDirection,
  WorkMode,
} from "@/lib/api";
import {
  applicationStageLabels,
  workModeLabels,
} from "./application-labels";

export const applicationStages: ApplicationStage[] = [
  "initial",
  "applied",
  "interviewing",
  "offer",
  "rejected",
];

export const stageFilterOptions: Array<{
  label: string;
  value: "all" | ApplicationStage;
}> = [
  { label: "All stages", value: "all" },
  ...applicationStages.map((stage) => ({
    label: applicationStageLabels[stage],
    value: stage,
  })),
];

export const workModeOptions: Array<{ label: string; value: WorkMode | "" }> = [
  { label: "Not set", value: "" },
  { label: workModeLabels.remote, value: "remote" },
  { label: workModeLabels.hybrid, value: "hybrid" },
  { label: workModeLabels.onsite, value: "onsite" },
];

export const interviewTypes: InterviewType[] = [
  "recruiter",
  "hr",
  "technical",
  "hiring_manager",
  "team_match",
  "home_assignment",
  "behavioral",
  "other",
];

export const interviewStatuses: InterviewStatus[] = [
  "planned",
  "scheduled",
  "completed",
  "passed",
  "rejected",
];

export const applicationSortFieldOptions: Array<{
  label: string;
  value: ApplicationSortField;
}> = [
  { label: "Updated", value: "updatedAt" },
  { label: "Created", value: "createdAt" },
];

export const sortDirectionOptions: Array<{
  label: string;
  value: SortDirection;
}> = [
  { label: "Descending", value: "desc" },
  { label: "Ascending", value: "asc" },
];
