import { InterviewStatus, InterviewType } from "@/lib/api";

export const MAX_INTERVIEW_ROWS = 10;

export type InterviewDraftRow = {
  rowId: string;
  interviewId: string | null;
  type: InterviewType | "";
  status: InterviewStatus;
  scheduledAt: string;
};
