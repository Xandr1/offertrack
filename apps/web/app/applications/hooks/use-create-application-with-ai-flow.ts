"use client";

import { FormEvent, useCallback, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createApplicationDraft } from "@/lib/api";
import type { ApplicationDraftResponse } from "@/lib/api";
import {
  getRequestErrorMessage,
  redirectToLoginIfProtectedRoute,
} from "@/lib/request-errors";
import { normalizeAiJobUrlInput } from "../helpers/ai-job-url-input";
import {
  ApplicationFormState,
  initialApplicationFormState,
} from "../models/application-form-model";
import {
  InterviewDraftRow,
  MAX_INTERVIEW_ROWS,
} from "../models/interview-row-model";
import type { useApplicationModalController } from "./use-application-modal-controller";

type ApplicationModalController = ReturnType<typeof useApplicationModalController>;

type UseCreateApplicationWithAiFlowParams = {
  clearPageError: () => void;
  modalController: ApplicationModalController;
  prepareCreateApplicationModal: () => void;
};

const createDraftRowId = (index: number): string => {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }

  return `ai-draft-row-${Date.now()}-${index}-${Math.random()
    .toString(16)
    .slice(2)}`;
};

const toInitialDraftForm = (
  draft: ApplicationDraftResponse,
): ApplicationFormState => {
  return {
    ...initialApplicationFormState,
    companyName: draft.companyName ?? "",
    positionTitle: draft.positionTitle ?? "",
    jobUrl: draft.jobUrl,
    location: draft.location ?? "",
    workMode: draft.workMode ?? "",
    stage: "initial",
    notes: draft.notes ?? "",
  };
};

const toInitialDraftRows = (
  draft: ApplicationDraftResponse,
): InterviewDraftRow[] => {
  return draft.interviews.slice(0, MAX_INTERVIEW_ROWS).map((interview, index) => ({
    rowId: createDraftRowId(index),
    interviewId: null,
    type: interview.type,
    status: "initial",
    scheduledAt: "",
  }));
};

export const useCreateApplicationWithAiFlow = ({
  clearPageError,
  modalController,
  prepareCreateApplicationModal,
}: UseCreateApplicationWithAiFlowParams) => {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [isCreateWithAiGenerating, setIsCreateWithAiGenerating] = useState(false);
  const [createWithAiError, setCreateWithAiError] = useState<string | null>(null);
  const activeAiDraftRequestRef = useRef(0);
  const createApplicationDraftMutation = useMutation({
    mutationFn: createApplicationDraft,
  });

  const applyDraftToCreateModal = useCallback(
    (draft: ApplicationDraftResponse) => {
      modalController.applyAiDraft({
        form: toInitialDraftForm(draft),
        rows: toInitialDraftRows(draft),
        warnings: draft.warnings
          .map((warning) => warning.trim())
          .filter((warning) => warning !== ""),
      });
    },
    [modalController],
  );

  const openCreateApplicationModal = useCallback(() => {
    clearPageError();
    prepareCreateApplicationModal();
    activeAiDraftRequestRef.current += 1;
    setIsCreateWithAiGenerating(false);
    setCreateWithAiError(null);
    modalController.openCreateModal();
  }, [
    clearPageError,
    modalController,
    prepareCreateApplicationModal,
  ]);

  const updateCreateWithAiJobUrl = useCallback((value: string) => {
    modalController.updateFormField("jobUrl", value);
    setCreateWithAiError(null);
  }, [modalController]);

  const handleCreateWithAiSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();

      const normalized = normalizeAiJobUrlInput(modalController.form.jobUrl);
      if (normalized.error !== null) {
        setCreateWithAiError(normalized.error);
        return;
      }

      const jobUrl = normalized.jobUrl;
      modalController.updateFormField("jobUrl", jobUrl);
      const requestId = activeAiDraftRequestRef.current + 1;
      activeAiDraftRequestRef.current = requestId;
      setCreateWithAiError(null);
      setIsCreateWithAiGenerating(true);

      try {
        const draft = await createApplicationDraftMutation.mutateAsync({
          jobUrl,
        });

        if (activeAiDraftRequestRef.current !== requestId) {
          return;
        }

        applyDraftToCreateModal(draft);
      } catch (error) {
        if (activeAiDraftRequestRef.current !== requestId) {
          return;
        }

        if (await redirectToLoginIfProtectedRoute(error, router, queryClient)) {
          return;
        }

        setCreateWithAiError(getRequestErrorMessage(error));
      } finally {
        if (activeAiDraftRequestRef.current === requestId) {
          setIsCreateWithAiGenerating(false);
        }
      }
    },
    [
      createApplicationDraftMutation,
      applyDraftToCreateModal,
      modalController,
      queryClient,
      router,
    ],
  );

  return {
    createApplicationDraftMutation,
    createWithAiError,
    createWithAiJobUrl: modalController.form.jobUrl,
    handleCreateWithAiSubmit,
    isCreateWithAiGenerating,
    openCreateApplicationModal,
    setCreateWithAiJobUrl: updateCreateWithAiJobUrl,
  };
};
