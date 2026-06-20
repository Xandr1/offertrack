"use client";

import { FormEventHandler } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { formStyles, modalStyles, textStyles } from "@/lib/styles";
import { ApplicationModal } from "./application-modal";

type CreateWithAiModalProps = {
  errorMessage: string | null;
  isGenerating: boolean;
  isOpen: boolean;
  jobUrl: string;
  onClose: () => void;
  onJobUrlChange: (value: string) => void;
  onSubmit: FormEventHandler<HTMLFormElement>;
};

export const CreateWithAiModal = ({
  errorMessage,
  isGenerating,
  isOpen,
  jobUrl,
  onClose,
  onJobUrlChange,
  onSubmit,
}: CreateWithAiModalProps) => {
  return (
    <ApplicationModal
      description="Generate an editable application draft from a public job URL."
      isCompact
      isOpen={isOpen}
      title="Create with AI"
      onClose={onClose}
    >
      <form className="space-y-4" onSubmit={onSubmit}>
        <div>
          <label className={textStyles.label}>Job URL</label>
          <Input
            className="mt-1.5"
            disabled={isGenerating}
            inputMode="url"
            maxLength={2048}
            placeholder="https://company.com/jobs/123"
            type="text"
            value={jobUrl}
            onChange={(event) => onJobUrlChange(event.target.value)}
          />
        </div>

        {errorMessage && <div className={formStyles.error}>{errorMessage}</div>}

        <div className={modalStyles.softFooterBleedCompact}>
          <Button onClick={onClose} variant="secondarySoft">
            Cancel
          </Button>
          <Button disabled={isGenerating} type="submit" variant="primarySoft">
            {isGenerating ? "Generating..." : "Generate draft"}
          </Button>
        </div>
      </form>
    </ApplicationModal>
  );
};
