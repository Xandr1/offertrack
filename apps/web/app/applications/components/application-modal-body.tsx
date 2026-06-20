"use client";

import { FormEventHandler } from "react";
import { sectionStyles, textStyles } from "@/lib/styles";
import {
  ApplicationDetailsSection,
  ApplicationDetailsSectionProps,
} from "./application-details-section";
import {
  ApplicationInterviewsSection,
  ApplicationInterviewsSectionProps,
} from "./application-interviews-section";
import {
  ApplicationModalFooter,
  ApplicationModalFooterProps,
} from "./application-modal-footer";

type ApplicationModalBodyProps = {
  footerSection: ApplicationModalFooterProps;
  formSection: ApplicationDetailsSectionProps;
  interviewsSection: ApplicationInterviewsSectionProps;
  draftWarnings?: string[];
  onSubmit: FormEventHandler<HTMLFormElement>;
};

export const ApplicationModalBody = ({
  draftWarnings = [],
  footerSection,
  formSection,
  interviewsSection,
  onSubmit,
}: ApplicationModalBodyProps) => {
  return (
    <form className="space-y-3" onSubmit={onSubmit}>
      {draftWarnings.length > 0 && (
        <div className={sectionStyles.softPanel}>
          <p className={textStyles.label}>AI draft warnings</p>
          <ul className="mt-1 list-disc space-y-1 pl-4 text-sm text-zinc-700">
            {draftWarnings.map((warning, index) => (
              <li key={`${warning}-${index}`}>{warning}</li>
            ))}
          </ul>
        </div>
      )}
      <ApplicationDetailsSection {...formSection} />
      <ApplicationInterviewsSection {...interviewsSection} />
      <ApplicationModalFooter {...footerSection} />
    </form>
  );
};
