"use client";

import { FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { formStyles, textStyles } from "@/lib/styles";
import { SettingsFormState } from "../models/settings-form-model";

type SettingsFormProps = {
  disabled: boolean;
  errorMessage: string | null;
  form: SettingsFormState;
  isSaving: boolean;
  successMessage: string | null;
  onFieldChange: <Key extends keyof SettingsFormState>(
    key: Key,
    value: SettingsFormState[Key],
  ) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export const SettingsForm = ({
  disabled,
  errorMessage,
  form,
  isSaving,
  successMessage,
  onFieldChange,
  onSubmit,
}: SettingsFormProps) => (
  <Card as="form" className="max-w-4xl space-y-5 p-4 sm:p-5" onSubmit={onSubmit}>
    <div>
      <h2 className={textStyles.sectionTitle}>Dashboard settings</h2>
    </div>

    <fieldset>
      <legend className="text-sm font-semibold text-zinc-950">
        Timing windows
      </legend>
      <div className="mt-4 grid gap-4 md:grid-cols-3">
        <div>
          <label className={textStyles.label} htmlFor="follow-up-after-applying">
            Follow up after applying
          </label>
          <Input
            className="mt-1.5"
            disabled={disabled}
            id="follow-up-after-applying"
            inputMode="numeric"
            max={60}
            min={1}
            required
            type="number"
            value={form.followUpAfterApplyingDays}
            onChange={(event) =>
              onFieldChange("followUpAfterApplyingDays", event.target.value)
            }
          />
        </div>

        <div>
          <label className={textStyles.label} htmlFor="upcoming-interviews-window">
            Upcoming interviews window
          </label>
          <Input
            className="mt-1.5"
            disabled={disabled}
            id="upcoming-interviews-window"
            inputMode="numeric"
            max={60}
            min={1}
            required
            type="number"
            value={form.upcomingInterviewDays}
            onChange={(event) =>
              onFieldChange("upcomingInterviewDays", event.target.value)
            }
          />
        </div>

        <div>
          <label className={textStyles.label} htmlFor="follow-up-after-interview">
            Follow up after interview
          </label>
          <Input
            className="mt-1.5"
            disabled={disabled}
            id="follow-up-after-interview"
            inputMode="numeric"
            max={30}
            min={1}
            required
            type="number"
            value={form.followUpAfterInterviewDays}
            onChange={(event) =>
              onFieldChange("followUpAfterInterviewDays", event.target.value)
            }
          />
        </div>
      </div>
    </fieldset>

    <div className="border-t border-zinc-200 pt-5">
      <label className={textStyles.label} htmlFor="target-role">
        Target role
      </label>
      <Input
        className="mt-1.5"
        disabled={disabled}
        id="target-role"
        maxLength={160}
        placeholder="Senior Backend Engineer"
        type="text"
        value={form.targetRole}
        onChange={(event) => onFieldChange("targetRole", event.target.value)}
      />
    </div>

    {errorMessage && <div className={formStyles.error}>{errorMessage}</div>}

    {successMessage && (
      <div
        className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700"
        role="status"
      >
        {successMessage}
      </div>
    )}

    <div className="flex justify-start border-t border-zinc-200 pt-4">
      <Button disabled={disabled || isSaving} type="submit" variant="primarySoft">
        {isSaving ? "Saving..." : "Save changes"}
      </Button>
    </div>
  </Card>
);
