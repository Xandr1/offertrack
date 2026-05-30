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

const normalizeWorkMode = (value: ApplicationFormState["workMode"]): WorkMode | null => {
  return value === "" ? null : value;
};

const toApplicationPayload = (
  form: ApplicationFormState,
): Omit<ReplaceApplicationRequest, "interviews"> => {
  return {
    companyName: form.companyName.trim(),
    positionTitle: form.positionTitle.trim(),
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
    location: application.location ?? "",
    workMode: application.workMode ?? "",
    stage: application.stage,
    appliedAt: toDateInputFromApi(application.appliedAt),
    notes: application.notes ?? "",
  };
};
