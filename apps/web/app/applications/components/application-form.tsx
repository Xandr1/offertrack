"use client";

import { WorkMode } from "@/lib/api";
import { formStyles, textStyles } from "@/lib/styles";
import { workModeOptions } from "../helpers/constants";
import { ApplicationFormState } from "../models/application-form-model";

type ApplicationFormProps = {
  disabled: boolean;
  form: ApplicationFormState;
  onFieldChange: <Key extends keyof ApplicationFormState>(
    key: Key,
    value: ApplicationFormState[Key],
  ) => void;
};

export const ApplicationForm = ({
  disabled,
  form,
  onFieldChange,
}: ApplicationFormProps) => {
  return (
    <div className="space-y-3">
      <div className="grid gap-4 md:grid-cols-2">
        <div>
          <label className={textStyles.label}>Company</label>
          <input
            className={formStyles.inputSoftSpaced}
            disabled={disabled}
            required
            value={form.companyName}
            onChange={(event) => onFieldChange("companyName", event.target.value)}
          />
        </div>

        <div>
          <label className={textStyles.label}>Position</label>
          <input
            className={formStyles.inputSoftSpaced}
            disabled={disabled}
            required
            value={form.positionTitle}
            onChange={(event) =>
              onFieldChange("positionTitle", event.target.value)
            }
          />
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <div>
          <label className={textStyles.label}>Location</label>
          <input
            className={formStyles.inputSoftSpaced}
            disabled={disabled}
            value={form.location}
            onChange={(event) => onFieldChange("location", event.target.value)}
          />
        </div>

        <div>
          <label className={textStyles.label}>Work mode</label>
          <select
            className={formStyles.selectSoftSpaced}
            disabled={disabled}
            value={form.workMode}
            onChange={(event) =>
              onFieldChange("workMode", event.target.value as WorkMode | "")
            }
          >
            {workModeOptions.map((option) => (
              <option key={option.label} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className={textStyles.label}>Applied date</label>
          <input
            className={formStyles.inputSoftSpaced}
            disabled={disabled}
            type="date"
            value={form.appliedAt}
            onChange={(event) => onFieldChange("appliedAt", event.target.value)}
          />
        </div>
      </div>

      <div>
        <label className={textStyles.label}>Notes</label>
        <textarea
          className={formStyles.textareaSoftSpaced}
          disabled={disabled}
          rows={3}
          value={form.notes}
          onChange={(event) => onFieldChange("notes", event.target.value)}
        />
      </div>
    </div>
  );
};
