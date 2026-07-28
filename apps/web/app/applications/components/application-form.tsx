"use client";

import { WorkMode } from "@/lib/api";
import { Input, Select, Textarea } from "@/components/ui/input";
import { textStyles } from "@/lib/styles";
import { workModeOptions } from "../helpers/constants";
import { ApplicationFormState } from "../models/application-form-model";
import { ApplicationStageSelect } from "./application-stage-select";

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
    <div className="space-y-6">
      <section aria-labelledby="application-role-heading">
        <div>
          <h3 className={textStyles.sectionTitle} id="application-role-heading">
            Role
          </h3>
          <p className={textStyles.description}>
            The company, role, and source job post.
          </p>
        </div>

        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div>
          <label className={textStyles.label} htmlFor="application-company">
            Company
          </label>
          <Input
            id="application-company"
            className="mt-1.5"
            autoComplete="organization"
            disabled={disabled}
            required
            value={form.companyName}
            onChange={(event) => onFieldChange("companyName", event.target.value)}
          />
          </div>

          <div>
          <label className={textStyles.label} htmlFor="application-position">
            Position
          </label>
          <Input
            id="application-position"
            className="mt-1.5"
            autoComplete="organization-title"
            disabled={disabled}
            required
            value={form.positionTitle}
            onChange={(event) =>
              onFieldChange("positionTitle", event.target.value)
            }
          />
          </div>

          <div className="sm:col-span-2 lg:col-span-1">
          <label className={textStyles.label} htmlFor="application-job-url">
            Job URL
          </label>
          <Input
            id="application-job-url"
            className="mt-1.5"
            autoComplete="url"
            disabled={disabled}
            inputMode="url"
            maxLength={2048}
            placeholder="google.com/careers/job-00001"
            type="text"
            value={form.jobUrl}
            onChange={(event) => onFieldChange("jobUrl", event.target.value)}
          />
          </div>
        </div>
      </section>

      <section
        aria-labelledby="application-details-heading"
        className="border-t border-zinc-200 pt-5"
      >
        <div>
          <h3
            className={textStyles.sectionTitle}
            id="application-details-heading"
          >
            Application details
          </h3>
          <p className={textStyles.description}>
            Context for tracking this application.
          </p>
        </div>

        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <label className={textStyles.label} htmlFor="application-location">
              Location
            </label>
            <Input
              id="application-location"
              className="mt-1.5"
              autoComplete="address-level2"
              disabled={disabled}
              value={form.location}
              onChange={(event) => onFieldChange("location", event.target.value)}
            />
          </div>

          <div>
            <label className={textStyles.label} htmlFor="application-work-mode">
              Work mode
            </label>
            <Select
              id="application-work-mode"
              className="mt-1.5"
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
            </Select>
          </div>

          <div>
            <label
              className={textStyles.label}
              htmlFor="application-applied-date"
            >
              Applied date
            </label>
            <Input
              id="application-applied-date"
              className="mt-1.5"
              disabled={disabled}
              type="date"
              value={form.appliedAt}
              onChange={(event) => onFieldChange("appliedAt", event.target.value)}
            />
          </div>

          <ApplicationStageSelect
            disabled={disabled}
            id="application-stage"
            label="Application stage"
            stage={form.stage}
            onChange={(stage) => onFieldChange("stage", stage)}
          />
        </div>
      </section>

      <section
        aria-labelledby="application-notes-heading"
        className="border-t border-zinc-200 pt-5"
      >
        <h3 className={textStyles.sectionTitle} id="application-notes-heading">
          Notes
        </h3>
        <p className={textStyles.description}>
          Add context you will want when following up.
        </p>
        <label className="sr-only" htmlFor="application-notes">
          Notes
        </label>
        <Textarea
          id="application-notes"
          className="mt-3"
          disabled={disabled}
          rows={4}
          value={form.notes}
          onChange={(event) => onFieldChange("notes", event.target.value)}
        />
      </section>
    </div>
  );
};
