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
  onSubmit: FormEventHandler<HTMLFormElement>;
};

export const ApplicationModalBody = ({
  footerSection,
  formSection,
  interviewsSection,
  onSubmit,
}: ApplicationModalBodyProps) => {
  return (
    <form className="space-y-3" onSubmit={onSubmit}>
      <ApplicationDetailsSection {...formSection} />
      <ApplicationInterviewsSection {...interviewsSection} />
      <ApplicationModalFooter {...footerSection} />
    </form>
  );
};
