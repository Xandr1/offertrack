"use client";

import { ApplicationFormState } from "../models/application-form-model";
import { ApplicationForm } from "./application-form";
import { ApplicationStageSelect } from "./application-stage-select";

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
    <>
      <ApplicationForm
        disabled={disabled}
        form={form}
        onFieldChange={onFieldChange}
      />
      <ApplicationStageSelect
        disabled={disabled}
        label="Application stage"
        labelClassName="text-sm font-semibold text-zinc-950"
        selectClassName="h-10 !bg-violet-50 text-[15px] font-semibold text-violet-800 disabled:!bg-violet-50"
        stage={form.stage}
        wrapperClassName="w-full md:max-w-[260px]"
        onChange={(stage) => onFieldChange("stage", stage)}
      />
    </>
  );
};
