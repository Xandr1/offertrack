"use client";

import { ApplicationFormState } from "../models/application-form-model";
import { ApplicationForm } from "./application-form";

export type ApplicationDetailsSectionProps = {
  disabled: boolean;
  form: ApplicationFormState;
  onFieldChange: <Key extends keyof ApplicationFormState>(
    key: Key,
    value: ApplicationFormState[Key],
  ) => void;
};

export const ApplicationDetailsSection = ({
  disabled,
  form,
  onFieldChange,
}: ApplicationDetailsSectionProps) => {
  return (
    <ApplicationForm
      disabled={disabled}
      form={form}
      onFieldChange={onFieldChange}
    />
  );
};
