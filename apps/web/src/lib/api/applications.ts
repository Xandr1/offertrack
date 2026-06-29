import { z } from "zod";
import { request } from "./client";
import {
  applicationDraftResponseSchema,
  applicationSchema,
  applicationsPageSchema,
  applicationBoardColumnSchema,
  applicationBoardSchema,
  applicationWithInterviewsSchema,
} from "./schemas";
import type {
  Application,
  ApplicationDraftRequest,
  ApplicationDraftResponse,
  ApplicationsListParams,
  ApplicationsBoardParams,
  ApplicationBoard,
  ApplicationBoardColumn,
  ApplicationBoardColumnParams,
  ApplicationsPage,
  ApplicationWithInterviews,
  CreateApplicationRequest,
  ReplaceApplicationRequest,
  UpdateApplicationStageRequest,
} from "./types";

export const APPLICATIONS_LIST_DEFAULTS: ApplicationsListParams = {
  direction: "desc",
  page: 0,
  search: "",
  size: 20,
  sort: "updatedAt",
  stage: null,
};

const buildApplicationsListPath = (params: ApplicationsListParams): string => {
  const searchParams = new URLSearchParams();
  const trimmedSearch = params.search.trim();

  if (params.page !== APPLICATIONS_LIST_DEFAULTS.page) {
    searchParams.set("page", String(params.page));
  }

  if (params.size !== APPLICATIONS_LIST_DEFAULTS.size) {
    searchParams.set("size", String(params.size));
  }

  if (trimmedSearch !== "") {
    searchParams.set("search", trimmedSearch);
  }

  if (params.stage !== null) {
    searchParams.set("stage", params.stage);
  }

  if (params.sort !== APPLICATIONS_LIST_DEFAULTS.sort) {
    searchParams.set("sort", params.sort);
  }

  if (params.direction !== APPLICATIONS_LIST_DEFAULTS.direction) {
    searchParams.set("direction", params.direction);
  }

  const query = searchParams.toString();
  return query ? `/api/applications?${query}` : "/api/applications";
};

export const listApplications = (
  params: ApplicationsListParams,
): Promise<ApplicationsPage> => {
  return request<ApplicationsPage>(
    buildApplicationsListPath(params),
    applicationsPageSchema,
  );
};

const appendBoardParams = (
  searchParams: URLSearchParams,
  params: ApplicationsBoardParams,
) => {
  const trimmedSearch = params.search.trim();
  if (trimmedSearch !== "") {
    searchParams.set("search", trimmedSearch);
  }
  searchParams.set("sort", params.sort);
  searchParams.set("direction", params.direction);
};

export const getApplicationsBoard = (
  params: ApplicationsBoardParams,
): Promise<ApplicationBoard> => {
  const searchParams = new URLSearchParams();
  appendBoardParams(searchParams, params);

  return request<ApplicationBoard>(
    `/api/applications/board?${searchParams.toString()}`,
    applicationBoardSchema,
  );
};

export const getApplicationBoardColumn = ({
  stage,
  search,
  sort,
  direction,
  offset,
}: ApplicationBoardColumnParams): Promise<ApplicationBoardColumn> => {
  const searchParams = new URLSearchParams();
  appendBoardParams(searchParams, { search, sort, direction });
  searchParams.set("offset", String(offset));

  return request<ApplicationBoardColumn>(
    `/api/applications/board/columns/${stage}?${searchParams.toString()}`,
    applicationBoardColumnSchema,
  );
};

export const getApplication = (id: string): Promise<Application> => {
  return request<Application>(`/api/applications/${id}`, applicationSchema);
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

export const createApplicationDraft = (
  payload: ApplicationDraftRequest,
): Promise<ApplicationDraftResponse> => {
  return request<ApplicationDraftResponse>(
    "/api/applications/draft",
    applicationDraftResponseSchema,
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
