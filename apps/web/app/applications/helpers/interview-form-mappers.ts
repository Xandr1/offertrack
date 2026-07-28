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

const toInterviewPayload = (
  row: InterviewDraftRow,
): Omit<ReplaceApplicationInterviewItemRequest, "id"> => ({
    type: row.type,
    status: row.status,
    scheduledAt: dateTimeLocalToApiDateTime(row.scheduledAt),
});

export const toCreateInterviewPayload = (
  row: InterviewDraftRow,
): CreateApplicationInterviewItemRequest => {
  return toInterviewPayload(row);
};

export const toReplaceInterviewPayload = (
  row: InterviewDraftRow,
): ReplaceApplicationInterviewItemRequest => {
  const payload = toInterviewPayload(row);

  return row.interviewId ? { ...payload, id: row.interviewId } : payload;
};
