import { request } from "./client";
import { applicationInterviewSchema, applicationInterviewsSchema } from "./schemas";
import {
  ApplicationInterview,
  UpdateInterviewStatusRequest,
} from "./types";

export const listApplicationInterviews = (
  applicationId: string,
): Promise<ApplicationInterview[]> => {
  return request<ApplicationInterview[]>(
    `/api/applications/${applicationId}/interviews`,
    applicationInterviewsSchema,
  );
};

export const updateApplicationInterviewStatus = (
  applicationId: string,
  interviewId: string,
  status: UpdateInterviewStatusRequest["status"],
): Promise<ApplicationInterview> => {
  const payload: UpdateInterviewStatusRequest = { status };

  return request<ApplicationInterview>(
    `/api/applications/${applicationId}/interviews/${interviewId}/status`,
    applicationInterviewSchema,
    {
      method: "PATCH",
      body: JSON.stringify(payload),
    },
  );
};
