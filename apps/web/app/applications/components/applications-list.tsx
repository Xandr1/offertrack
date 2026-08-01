"use client";

import { Application, ApplicationStage, InterviewStatus } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import {
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
  hasActiveFilters: boolean;
  nextInterviewStatusApplicationId?: string;
  stageUpdatingApplicationId?: string;
  onDelete: (application: Application) => void;
  onClearFilters: () => void;
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
  hasActiveFilters,
  isLoading,
  nextInterviewStatusApplicationId,
  onClearFilters,
  onDelete,
  onEdit,
  onNextInterviewStatusChange,
  onRetry,
  onStageChange,
  stageUpdatingApplicationId,
}: ApplicationsListProps) => {
  if (isLoading) {
    return (
      <Card variant="soft">
        <p className={textStyles.muted}>Loading applications...</p>
      </Card>
    );
  }

  if (errorMessage) {
    return (
      <Card variant="soft">
        <div className={formStyles.error}>{errorMessage}</div>
        <Button className="mt-2" onClick={onRetry} variant="secondarySoft">
          Retry
        </Button>
      </Card>
    );
  }

  if (applications.length === 0) {
    return (
      <EmptyState
        action={
          hasActiveFilters ? (
            <Button onClick={onClearFilters} variant="secondarySoft">
              Clear filters
            </Button>
          ) : undefined
        }
        description={
          hasActiveFilters
            ? "Try clearing your search or stage and sort filters."
            : "Add your first role to start tracking its progress."
        }
        title={
          hasActiveFilters
            ? "No matching applications"
            : "No applications yet"
        }
      />
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
