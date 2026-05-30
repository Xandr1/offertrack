"use client";

import { ApplicationStage } from "@/lib/api";
import { formStyles, textStyles } from "@/lib/styles";
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
  const baseSelectClass = showLabel
    ? formStyles.selectSoftSpaced
    : formStyles.selectSoft;
  const inputClassName = [
    baseSelectClass,
    selectClassName ?? "",
  ]
    .join(" ")
    .trim();

  return (
    <div className={wrapperClassName}>
      {showLabel && (
        <label className={labelClassName ?? textStyles.label}>{label}</label>
      )}
      <select
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
      </select>
    </div>
  );
};
