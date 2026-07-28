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
  <form className="space-y-5" onSubmit={onSubmit}>
    <section className="mx-auto max-w-2xl py-2 sm:py-5">
      <div className="flex items-start gap-3">
        <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-violet-100 text-violet-700">
          <IconSparkles className="h-4 w-4" />
        </span>
        <div>
          <h3 className={textStyles.sectionTitle}>Generate an editable draft</h3>
          <p className={textStyles.description} id={helpId}>
            Paste a public job post URL. Generated details fill only empty
            fields, so anything you entered manually is kept.
          </p>
        </div>
      </div>

      <div className="mt-5">
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

    <div className={modalStyles.softFooterBleed}>
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
