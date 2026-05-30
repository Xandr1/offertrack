import {
  ApplicationInterview,
  CreateApplicationInterviewItemRequest,
  ReplaceApplicationInterviewItemRequest,
} from "@/lib/api";
import {
  apiDateTimeToDateTimeLocal,
  dateTimeLocalToApiDateTime,
} from "./application-date-helpers";
import { InterviewDraftRow } from "../models/interview-row-model";

export type { InterviewDraftRow } from "../models/interview-row-model";

export const toInterviewDraftRow = (
  interview: ApplicationInterview,
): InterviewDraftRow => {
  return {
    rowId: interview.id,
    interviewId: interview.id,
    type: interview.type,
    status: interview.status,
    scheduledAt: apiDateTimeToDateTimeLocal(interview.scheduledAt),
  };
};

export const hasMissingInterviewType = (rows: InterviewDraftRow[]): boolean => {
  return rows.some((row) => row.type === "");
};

const toInterviewPayload = (
  row: InterviewDraftRow,
): Omit<ReplaceApplicationInterviewItemRequest, "id"> | null => {
  if (row.type === "") {
    return null;
  }

  return {
    type: row.type,
    status: row.status,
    scheduledAt: dateTimeLocalToApiDateTime(row.scheduledAt),
  };
};

export const toCreateInterviewPayload = (
  row: InterviewDraftRow,
): CreateApplicationInterviewItemRequest | null => {
  return toInterviewPayload(row);
};

export const toReplaceInterviewPayload = (
  row: InterviewDraftRow,
): ReplaceApplicationInterviewItemRequest | null => {
  const payload = toInterviewPayload(row);
  if (!payload) {
    return null;
  }

  return row.interviewId ? { ...payload, id: row.interviewId } : payload;
};
