import { Settings, UpdateSettingsRequest } from "@/lib/api";
import { SettingsFormState } from "../models/settings-form-model";

type NumberRange = {
  label: string;
  max: number;
  min: number;
  value: string;
};

export const toSettingsFormState = (settings: Settings): SettingsFormState => ({
  followUpAfterApplyingDays: String(settings.followUpAfterApplyingDays),
  upcomingInterviewDays: String(settings.upcomingInterviewDays),
  followUpAfterInterviewDays: String(settings.followUpAfterInterviewDays),
  targetRole: settings.targetRole ?? "",
});

export const toUpdateSettingsPayload = (
  form: SettingsFormState,
): UpdateSettingsRequest => ({
  followUpAfterApplyingDays: Number(form.followUpAfterApplyingDays),
  upcomingInterviewDays: Number(form.upcomingInterviewDays),
  followUpAfterInterviewDays: Number(form.followUpAfterInterviewDays),
  targetRole: normalizeOptionalString(form.targetRole),
});

export const getSettingsFormValidationError = (
  form: SettingsFormState,
): string | null => {
  const ranges: NumberRange[] = [
    {
      label: "Follow up after applying",
      max: 60,
      min: 1,
      value: form.followUpAfterApplyingDays,
    },
    {
      label: "Upcoming interviews window",
      max: 60,
      min: 1,
      value: form.upcomingInterviewDays,
    },
    {
      label: "Follow up after interview",
      max: 30,
      min: 1,
      value: form.followUpAfterInterviewDays,
    },
  ];

  for (const range of ranges) {
    const value = Number(range.value);

    if (!Number.isInteger(value) || value < range.min || value > range.max) {
      return `${range.label} must be between ${range.min} and ${range.max} days.`;
    }
  }

  if (form.targetRole.trim().length > 160) {
    return "Target role must be at most 160 characters.";
  }

  return null;
};

const normalizeOptionalString = (value: string): string | null => {
  const normalized = value.trim();
  return normalized ? normalized : null;
};
