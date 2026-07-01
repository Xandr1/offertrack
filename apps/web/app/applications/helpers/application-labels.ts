import {
  ApplicationStage,
  InterviewStatus,
  InterviewType,
  WorkMode,
} from "@/lib/api";

export const applicationStageLabels: Record<ApplicationStage, string> = {
  initial: "Initial",
  applied: "Applied",
  interviewing: "Interviewing",
  offer: "Offer",
  rejected: "Rejected",
};

export const workModeLabels: Record<WorkMode, string> = {
  remote: "Remote",
  hybrid: "Hybrid",
  onsite: "Onsite",
};

export const interviewTypeLabels: Record<InterviewType, string> = {
  recruiter: "Recruiter",
  hr: "HR",
  technical: "Technical",
  hiring_manager: "Hiring Manager",
  team_match: "Team Matching",
  home_assignment: "Home Assignment",
  behavioral: "Behavioral",
  other: "Other",
};

export const interviewStatusLabels: Record<InterviewStatus, string> = {
  initial: "Initial",
  scheduled: "Scheduled",
  passed: "Passed",
  rejected: "Rejected",
};

export const mapInterviewTypeLabel = (type: InterviewType): string => {
  return interviewTypeLabels[type];
};

export const mapWorkModeLabel = (mode: WorkMode | null): string => {
  if (!mode) {
    return "";
  }

  return workModeLabels[mode];
};
