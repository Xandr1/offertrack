import { NextInterview } from "@/lib/api";
import { formatDateTime } from "@/lib/date-format";
import {
  interviewStatusLabels,
  mapInterviewTypeLabel,
} from "./application-labels";

export const getNextInterviewLabel = (nextInterview: NextInterview | null): string => {
  if (!nextInterview) {
    return "No upcoming interview";
  }

  const typeLabel = mapInterviewTypeLabel(nextInterview.type);

  if (nextInterview.scheduledAt) {
    return `Next interview: ${typeLabel} · ${formatDateTime(nextInterview.scheduledAt)}`;
  }

  return `Next interview: ${typeLabel} · ${interviewStatusLabels[nextInterview.status]}`;
};
