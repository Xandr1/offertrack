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
  <Card as="form" className="space-y-5" onSubmit={onSubmit}>
    <div>
      <h2 className={textStyles.sectionTitle}>Dashboard settings</h2>
      <p className={textStyles.description}>
        Tune the timing windows used by your dashboard action modules.
      </p>
    </div>

    <div className="grid gap-4 md:grid-cols-3">
      <div>
        <label className={textStyles.label}>Follow up after applying</label>
        <Input
          className="mt-1.5"
          disabled={disabled}
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
        <label className={textStyles.label}>Upcoming interviews window</label>
        <Input
          className="mt-1.5"
          disabled={disabled}
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
        <label className={textStyles.label}>Follow up after interview</label>
        <Input
          className="mt-1.5"
          disabled={disabled}
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

    <div>
      <label className={textStyles.label}>Target role</label>
      <Input
        className="mt-1.5"
        disabled={disabled}
        maxLength={160}
        placeholder="Senior Backend Engineer"
        type="text"
        value={form.targetRole}
        onChange={(event) => onFieldChange("targetRole", event.target.value)}
      />
    </div>

    {errorMessage && <div className={formStyles.error}>{errorMessage}</div>}

    {successMessage && (
      <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
        {successMessage}
      </div>
    )}

    <div className="flex justify-end">
      <Button disabled={disabled || isSaving} type="submit" variant="primarySoft">
        {isSaving ? "Saving..." : "Save changes"}
      </Button>
    </div>
  </Card>
);
