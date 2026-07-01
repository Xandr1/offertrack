"use client";

import { QueryClient, useMutation } from "@tanstack/react-query";
import {
  ApplicationInterview,
  InterviewStatus,
  updateApplicationInterviewStatus,
} from "@/lib/api";
import { invalidateAfterInterviewChange } from "../services/applications-cache-service";

type UpdateInterviewStatusVariables = {
  applicationId: string;
  interviewId: string;
  status: InterviewStatus;
};

type UseInterviewMutationsParams = {
  onMutationError: (error: unknown) => void | Promise<void>;
  queryClient: QueryClient;
};

export const useInterviewMutations = ({
  onMutationError,
  queryClient,
}: UseInterviewMutationsParams) => {
  const updateInterviewStatusMutation = useMutation<
    ApplicationInterview,
    unknown,
    UpdateInterviewStatusVariables
  >({
    mutationFn: ({ applicationId, interviewId, status }) =>
      updateApplicationInterviewStatus(applicationId, interviewId, status),
    onError: (mutationError) => {
      void onMutationError(mutationError);
    },
    onSettled: (_data, _error, variables) => {
      if (!variables) {
        return;
      }

      invalidateAfterInterviewChange(queryClient, {
        applicationId: variables.applicationId,
      });
    },
  });

  return {
    updateInterviewStatusMutation,
  };
};
