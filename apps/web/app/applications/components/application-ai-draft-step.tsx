"use client";

import type { FormEventHandler } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { formStyles, modalStyles, textStyles } from "@/lib/styles";
import { IconSparkles } from "./ui-icons";

type ApplicationAiDraftStepProps = {
  errorMessage: string | null;
  isGenerating: boolean;
  jobUrl: string;
  onCancel: () => void;
  onJobUrlChange: (value: string) => void;
  onSubmit: FormEventHandler<HTMLFormElement>;
};

const helpId = "application-ai-job-url-help";
const errorId = "application-ai-job-url-error";

export const ApplicationAiDraftStep = ({
  errorMessage,
  isGenerating,
  jobUrl,
  onCancel,
  onJobUrlChange,
  onSubmit,
}: ApplicationAiDraftStepProps) => (
  <form className="space-y-4" onSubmit={onSubmit}>
    <section>
      <p className={textStyles.subtitle} id={helpId}>
        Paste a public job post URL to generate an editable draft.
      </p>

      <div className="mt-4">
        <label className={textStyles.label} htmlFor="application-ai-job-url">
          Job URL
        </label>
        <Input
          aria-describedby={
            errorMessage ? `${helpId} ${errorId}` : helpId
          }
          aria-invalid={errorMessage ? "true" : undefined}
          autoComplete="url"
          className="mt-1.5"
          disabled={isGenerating}
          id="application-ai-job-url"
          inputMode="url"
          maxLength={2048}
          placeholder="https://company.com/jobs/123"
          type="text"
          value={jobUrl}
          onChange={(event) => onJobUrlChange(event.target.value)}
        />
        {errorMessage && (
          <div
            className={`mt-3 ${formStyles.error}`}
            id={errorId}
            role="alert"
          >
            {errorMessage}
          </div>
        )}
      </div>
    </section>

    <div className={modalStyles.softFooterBleedCompact}>
      <Button onClick={onCancel} variant="secondarySoft">
        Cancel
      </Button>
      <Button
        disabled={isGenerating}
        type="submit"
        variant="primarySoft"
      >
        <IconSparkles className="mr-2 h-4 w-4" />
        {isGenerating ? "Generating..." : "Generate draft"}
      </Button>
    </div>
  </form>
);
