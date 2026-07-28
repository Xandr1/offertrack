"use client";

import { FormEventHandler } from "react";
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
    <form className="space-y-6" onSubmit={onSubmit}>
      {draftWarnings.length > 0 && (
        <div
          className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5"
          role="note"
        >
          <p className="text-sm font-medium text-amber-900">
            AI draft warnings
          </p>
          <ul className="mt-1 list-disc space-y-1 pl-4 text-xs text-amber-900/80">
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
