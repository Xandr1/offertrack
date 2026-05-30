import {
  ApplicationStage,
  InterviewStatus,
  InterviewType,
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

export const sortOptions = [
  { label: "Newest updated", value: "updated_desc" },
  { label: "Oldest updated", value: "updated_asc" },
  { label: "Newest created", value: "created_desc" },
  { label: "Oldest created", value: "created_asc" },
] as const;
