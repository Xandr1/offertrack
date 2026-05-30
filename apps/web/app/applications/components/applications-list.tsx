"use client";

import { Application, ApplicationStage, InterviewStatus } from "@/lib/api";
import {
  buttonStyles,
  cardStyles,
  formStyles,
  layoutStyles,
  textStyles,
} from "@/lib/styles";
import { ApplicationCard } from "./application-card";

type ApplicationsListProps = {
  applications: Application[];
  deletingApplicationId?: string;
  errorMessage: string | null;
  isLoading: boolean;
  nextInterviewStatusApplicationId?: string;
  stageUpdatingApplicationId?: string;
  onDelete: (application: Application) => void;
  onEdit: (application: Application) => void;
  onNextInterviewStatusChange: (
    application: Application,
    status: InterviewStatus,
  ) => void;
  onRetry: () => void;
  onStageChange: (application: Application, stage: ApplicationStage) => void;
};

export const ApplicationsList = ({
  applications,
  deletingApplicationId,
  errorMessage,
  isLoading,
  nextInterviewStatusApplicationId,
  onDelete,
  onEdit,
  onNextInterviewStatusChange,
  onRetry,
  onStageChange,
  stageUpdatingApplicationId,
}: ApplicationsListProps) => {
  if (isLoading) {
    return (
      <div className={cardStyles.soft}>
        <p className={textStyles.muted}>Loading applications...</p>
      </div>
    );
  }

  if (errorMessage) {
    return (
      <div className={cardStyles.soft}>
        <div className={formStyles.error}>{errorMessage}</div>
        <button
          className={buttonStyles.secondarySoftWithTopMargin}
          onClick={onRetry}
          type="button"
        >
          Retry
        </button>
      </div>
    );
  }

  if (applications.length === 0) {
    return (
      <div className={cardStyles.dashed}>
        <h2 className={textStyles.sectionTitle}>No matching applications</h2>
        <p className={textStyles.description}>
          Try changing filters or add a new application.
        </p>
      </div>
    );
  }

  return (
    <div className={layoutStyles.stackMd}>
      {applications.map((application) => (
        <ApplicationCard
          application={application}
          key={application.id}
          isDeleting={deletingApplicationId === application.id}
          isUpdatingInterviewStatus={
            nextInterviewStatusApplicationId === application.id
          }
          isUpdatingStage={stageUpdatingApplicationId === application.id}
          onDelete={onDelete}
          onEdit={onEdit}
          onNextInterviewStatusChange={onNextInterviewStatusChange}
          onStageChange={onStageChange}
        />
      ))}
    </div>
  );
};
