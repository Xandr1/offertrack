"use client";

import { QueryClient, useMutation } from "@tanstack/react-query";
import {
  Application,
  ApplicationStage,
  deleteApplication,
  updateApplicationStage,
} from "@/lib/api";
import { invalidateAfterApplicationChange } from "../services/applications-cache-service";

type StageMutationVariables = {
  applicationId: string;
  stage: ApplicationStage;
};

type DeleteMutationVariables = {
  applicationId: string;
};

type UseApplicationMutationsParams = {
  onMutationError: (error: unknown) => void | Promise<void>;
  queryClient: QueryClient;
};

export const useApplicationMutations = ({
  onMutationError,
  queryClient,
}: UseApplicationMutationsParams) => {
  const updateStageMutation = useMutation<
    Application,
    unknown,
    StageMutationVariables
  >({
    mutationFn: ({ applicationId, stage }) =>
      updateApplicationStage(applicationId, stage),
    onError: (mutationError) => {
      void onMutationError(mutationError);
    },
    onSettled: (_data, _error, variables) => {
      invalidateAfterApplicationChange(queryClient, {
        applicationId: variables?.applicationId,
      });
    },
  });

  const deleteApplicationMutation = useMutation<void, unknown, DeleteMutationVariables>(
    {
      mutationFn: ({ applicationId }) => deleteApplication(applicationId),
      onError: (mutationError) => {
        void onMutationError(mutationError);
      },
      onSettled: (_data, _error, variables) => {
        invalidateAfterApplicationChange(queryClient, {
          applicationId: variables?.applicationId,
        });
      },
    },
  );

  return {
    deleteApplicationMutation,
    updateStageMutation,
  };
};
