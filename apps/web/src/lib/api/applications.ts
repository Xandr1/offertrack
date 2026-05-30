import { z } from "zod";
import { request } from "./client";
import {
  applicationSchema,
  applicationsSchema,
  applicationWithInterviewsSchema,
} from "./schemas";
import {
  Application,
  ApplicationWithInterviews,
  CreateApplicationRequest,
  ReplaceApplicationRequest,
  UpdateApplicationStageRequest,
} from "./types";

export const listApplications = (): Promise<Application[]> => {
  return request<Application[]>("/api/applications", applicationsSchema);
};

export const createApplication = (
  payload: CreateApplicationRequest,
): Promise<ApplicationWithInterviews> => {
  return request<ApplicationWithInterviews>(
    "/api/applications",
    applicationWithInterviewsSchema,
    {
      method: "POST",
      body: JSON.stringify(payload),
    },
  );
};

export const replaceApplication = (
  id: string,
  payload: ReplaceApplicationRequest,
): Promise<ApplicationWithInterviews> => {
  return request<ApplicationWithInterviews>(
    `/api/applications/${id}`,
    applicationWithInterviewsSchema,
    {
      method: "PUT",
      body: JSON.stringify(payload),
    },
  );
};

export const updateApplicationStage = (
  id: string,
  stage: UpdateApplicationStageRequest["stage"],
): Promise<Application> => {
  const payload: UpdateApplicationStageRequest = { stage };

  return request<Application>(`/api/applications/${id}/stage`, applicationSchema, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
};

export const deleteApplication = (id: string): Promise<void> => {
  return request<void>(`/api/applications/${id}`, z.undefined(), {
    method: "DELETE",
  });
};
