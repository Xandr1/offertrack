"use client";

import { QueryClient, useMutation } from "@tanstack/react-query";
import { InterviewDraftRow } from "../models/interview-row-model";
import { ApplicationFormState } from "../models/application-form-model";
import { setSavedApplicationWithInterviews } from "../services/applications-cache-service";
import {
  SaveApplicationWithInterviewsResult,
  saveApplicationWithInterviews,
} from "../services/application-save-service";

type CreateApplicationSaveVariables = {
  form: ApplicationFormState;
  mode: "create";
  rows: InterviewDraftRow[];
};

type EditApplicationSaveVariables = {
  applicationId: string;
  form: ApplicationFormState;
  mode: "edit";
  rows: InterviewDraftRow[];
  pendingUndoRowIds?: string[];
};

type SaveApplicationVariables =
  | CreateApplicationSaveVariables
  | EditApplicationSaveVariables;

type UseApplicationSaveParams = {
  onMutationError: (error: unknown) => void | Promise<void>;
  queryClient: QueryClient;
};

export const useApplicationSave = ({
  onMutationError,
  queryClient,
}: UseApplicationSaveParams) => {
  const saveApplicationMutation = useMutation<
    SaveApplicationWithInterviewsResult,
    unknown,
    SaveApplicationVariables
  >({
    mutationFn: (variables) => saveApplicationWithInterviews(variables),
    onError: (error) => {
      void onMutationError(error);
    },
    onSuccess: (result) => {
      setSavedApplicationWithInterviews(queryClient, result);
    },
  });

  return {
    saveApplicationMutation,
  };
};
