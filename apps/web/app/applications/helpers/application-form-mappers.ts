import {
  Application,
  CreateApplicationRequest,
  ReplaceApplicationRequest,
  WorkMode,
} from "@/lib/api";
import { ApplicationFormState } from "../models/application-form-model";
import {
  toApiDateFromDateInput,
  toDateInputFromApi,
} from "./application-date-helpers";

const normalizeNullableString = (value: string): string | null => {
  const trimmedValue = value.trim();
  return trimmedValue === "" ? null : trimmedValue;
};

const explicitSchemePattern = /^[A-Za-z][A-Za-z0-9+.-]*:\/\//;
const ipv4HostPattern = /^(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?$/;

const looksLikeHostPath = (value: string): boolean => {
  const hostCandidate = value.split(/[/?#]/, 1)[0];
  if (!hostCandidate || hostCandidate.includes(" ")) {
    return false;
  }

  const hostWithoutPort = hostCandidate.replace(/:\d+$/, "");
  if (hostCandidate.includes(":") && hostWithoutPort === hostCandidate) {
    return false;
  }

  return (
    hostWithoutPort.toLowerCase() === "localhost" ||
    hostWithoutPort.includes(".") ||
    ipv4HostPattern.test(hostCandidate)
  );
};

const normalizeJobUrl = (value: string): string | null => {
  const trimmedValue = value.trim();
  if (trimmedValue === "") {
    return null;
  }

  if (
    explicitSchemePattern.test(trimmedValue) ||
    !looksLikeHostPath(trimmedValue)
  ) {
    return trimmedValue;
  }

  return `https://${trimmedValue}`;
};

const normalizeWorkMode = (value: ApplicationFormState["workMode"]): WorkMode | null => {
  return value === "" ? null : value;
};

const toApplicationPayload = (
  form: ApplicationFormState,
): Omit<ReplaceApplicationRequest, "interviews"> => {
  return {
    companyName: form.companyName.trim(),
    positionTitle: form.positionTitle.trim(),
    jobUrl: normalizeJobUrl(form.jobUrl),
    location: normalizeNullableString(form.location),
    workMode: normalizeWorkMode(form.workMode),
    stage: form.stage,
    notes: normalizeNullableString(form.notes),
    appliedAt: toApiDateFromDateInput(form.appliedAt),
  };
};

export const toCreatePayload = (
  form: ApplicationFormState,
): CreateApplicationRequest => {
  return toApplicationPayload(form);
};

export const toReplacePayload = (
  form: ApplicationFormState,
): Omit<ReplaceApplicationRequest, "interviews"> => {
  return toApplicationPayload(form);
};

export const toFormState = (
  application: Application,
): ApplicationFormState => {
  return {
    companyName: application.companyName,
    positionTitle: application.positionTitle,
    jobUrl: application.jobUrl ?? "",
    location: application.location ?? "",
    workMode: application.workMode ?? "",
    stage: application.stage,
    appliedAt: toDateInputFromApi(application.appliedAt),
    notes: application.notes ?? "",
  };
};
