"use client";

import { ApplicationStage } from "@/lib/api";
import { Select } from "@/components/ui/input";
import { textStyles } from "@/lib/styles";
import { applicationStageLabels } from "../helpers/application-labels";
import { applicationStages } from "../helpers/constants";

type ApplicationStageSelectProps = {
  disabled: boolean;
  labelClassName?: string;
  stage: ApplicationStage;
  label?: string;
  selectClassName?: string;
  showLabel?: boolean;
  wrapperClassName?: string;
  onChange: (stage: ApplicationStage) => void;
};

export const ApplicationStageSelect = ({
  disabled,
  labelClassName,
  label = "Stage",
  selectClassName,
  showLabel = true,
  stage,
  wrapperClassName,
  onChange,
}: ApplicationStageSelectProps) => {
  const inputClassName = [showLabel ? "mt-1.5" : "", selectClassName ?? ""]
    .join(" ")
    .trim();

  return (
    <div className={wrapperClassName}>
      {showLabel && (
        <label className={labelClassName ?? textStyles.label}>{label}</label>
      )}
      <Select
        className={inputClassName}
        disabled={disabled}
        value={stage}
        onChange={(event) => onChange(event.target.value as ApplicationStage)}
      >
        {applicationStages.map((stageOption) => (
          <option key={stageOption} value={stageOption}>
            {applicationStageLabels[stageOption]}
          </option>
        ))}
      </Select>
    </div>
  );
};
