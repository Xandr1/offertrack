"use client";

import { useCallback, useEffect, useRef } from "react";
import type { Application } from "@/lib/api";
import { isAuthError } from "@/lib/request-errors";
import type { useApplicationModalController } from "./use-application-modal-controller";
import type { useApplicationQuery } from "./use-application-query";

type ApplicationModalController = ReturnType<typeof useApplicationModalController>;
type ApplicationDetailQuery = ReturnType<typeof useApplicationQuery>;

type UseApplicationSelectionFlowParams = {
  applicationDetailQuery: ApplicationDetailQuery;
  clearPageError: () => void;
  clearSelectedApplicationId: () => void;
  modalController: ApplicationModalController;
  selectedApplicationId: string | null;
  setSelectedApplicationId: (applicationId: string) => void;
};

export const useApplicationSelectionFlow = ({
  applicationDetailQuery,
  clearPageError,
  clearSelectedApplicationId,
  modalController,
  selectedApplicationId,
  setSelectedApplicationId,
}: UseApplicationSelectionFlowParams) => {
  const openedDetailIdRef = useRef<string | null>(null);
  const suppressedDetailIdRef = useRef<string | null>(null);

  useEffect(() => {
    if (!selectedApplicationId) {
      openedDetailIdRef.current = null;
      suppressedDetailIdRef.current = null;

      if (modalController.isEditMode && !modalController.isSaving) {
        modalController.closeApplicationModal();
      }

      return;
    }

    if (suppressedDetailIdRef.current === selectedApplicationId) {
      return;
    }

    if (
      modalController.isEditMode &&
      modalController.selectedApplicationId !== selectedApplicationId &&
      !modalController.isSaving
    ) {
      modalController.closeApplicationModal();
    }

    if (
      !applicationDetailQuery.data ||
      openedDetailIdRef.current === selectedApplicationId
    ) {
      return;
    }

    clearPageError();
    modalController.openEditModal(applicationDetailQuery.data);
    openedDetailIdRef.current = selectedApplicationId;
  }, [
    applicationDetailQuery.data,
    clearPageError,
    modalController,
    selectedApplicationId,
  ]);

  const suppressAndClearSelectedApplicationId = useCallback(() => {
    if (!selectedApplicationId) {
      return;
    }

    suppressedDetailIdRef.current = selectedApplicationId;
    clearSelectedApplicationId();
  }, [clearSelectedApplicationId, selectedApplicationId]);

  const prepareCreateApplicationModal = useCallback(() => {
    clearPageError();
    openedDetailIdRef.current = null;
    suppressAndClearSelectedApplicationId();
  }, [clearPageError, suppressAndClearSelectedApplicationId]);

  const openEditApplicationModal = useCallback(
    (application: Application) => {
      clearPageError();
      suppressedDetailIdRef.current = null;
      setSelectedApplicationId(application.id);
    },
    [clearPageError, setSelectedApplicationId],
  );

  const closeApplicationModal = useCallback(() => {
    if (modalController.isSaving) {
      return;
    }

    modalController.closeApplicationModal();
    openedDetailIdRef.current = null;
    suppressAndClearSelectedApplicationId();
  }, [modalController, suppressAndClearSelectedApplicationId]);

  const clearSelectionAfterSave = useCallback(() => {
    openedDetailIdRef.current = null;
    suppressAndClearSelectedApplicationId();
  }, [suppressAndClearSelectedApplicationId]);

  const closeSelectedApplicationAfterDelete = useCallback(
    (applicationId: string) => {
      if (selectedApplicationId !== applicationId) {
        return;
      }

      openedDetailIdRef.current = null;
      suppressedDetailIdRef.current = selectedApplicationId;
      modalController.closeApplicationModal();
      clearSelectedApplicationId();
    },
    [
      clearSelectedApplicationId,
      modalController,
      selectedApplicationId,
    ],
  );

  const isApplicationDetailAuthError = isAuthError(applicationDetailQuery.error);
  const applicationDetailStatusMessage =
    selectedApplicationId && applicationDetailQuery.isPending
      ? "Loading application..."
      : selectedApplicationId &&
        applicationDetailQuery.isError &&
        !isApplicationDetailAuthError
        ? "Unable to open this application. It may have been deleted or you may not have access."
        : null;

  return {
    applicationDetailStatusKind: applicationDetailQuery.isError ? "error" : "loading",
    applicationDetailStatusMessage,
    clearSelectionAfterSave,
    closeApplicationModal,
    closeSelectedApplicationAfterDelete,
    openEditApplicationModal,
    prepareCreateApplicationModal,
  };
};
