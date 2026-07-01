"use client";

import { useCallback } from "react";
import type { Application, ApplicationStage, InterviewStatus } from "@/lib/api";
import type { useApplicationMutations } from "./use-application-mutations";
import type { useInterviewMutations } from "./use-interview-mutations";

type UpdateStageMutation =
  ReturnType<typeof useApplicationMutations>["updateStageMutation"];
type UpdateInterviewStatusMutation =
  ReturnType<typeof useInterviewMutations>["updateInterviewStatusMutation"];

type UseApplicationRowActionsParams = {
  clearPageError: () => void;
  updateInterviewStatusMutation: UpdateInterviewStatusMutation;
  updateStageMutation: UpdateStageMutation;
};

export const useApplicationRowActions = ({
  clearPageError,
  updateInterviewStatusMutation,
  updateStageMutation,
}: UseApplicationRowActionsParams) => {
  const handleStageChange = useCallback(
    (application: Application, stage: ApplicationStage) => {
      if (application.stage === stage) {
        return;
      }

      clearPageError();
      updateStageMutation.mutate({
        applicationId: application.id,
        stage,
      });
    },
    [clearPageError, updateStageMutation],
  );

  const handleNextInterviewStatusChange = useCallback(
    (application: Application, status: InterviewStatus) => {
      const interview = application.nextInterview ?? application.lastInterview;
      if (!interview || interview.status === status) {
        return;
      }

      clearPageError();
      updateInterviewStatusMutation.mutate({
        applicationId: application.id,
        interviewId: interview.id,
        status,
      });
    },
    [clearPageError, updateInterviewStatusMutation],
  );

  return {
    handleNextInterviewStatusChange,
    handleStageChange,
    nextInterviewStatusApplicationId:
      updateInterviewStatusMutation.variables?.applicationId,
    stageUpdatingApplicationId: updateStageMutation.variables?.applicationId,
  };
};
