"use client";

import { FormEvent, useCallback } from "react";
import type { ApplicationsView } from "../helpers/application-filters";
import type { useApplicationModalController } from "./use-application-modal-controller";
import type { useApplicationSave } from "./use-application-save";
import type { useApplicationsBoardController } from "./use-applications-board-controller";

type ApplicationModalController = ReturnType<typeof useApplicationModalController>;
type ApplicationsBoardController = ReturnType<typeof useApplicationsBoardController>;
type SaveApplicationMutation =
  ReturnType<typeof useApplicationSave>["saveApplicationMutation"];

type UseApplicationSaveFlowParams = {
  boardController: ApplicationsBoardController;
  clearSelectionAfterSave: () => void;
  modalController: ApplicationModalController;
  saveApplicationMutation: SaveApplicationMutation;
  view: ApplicationsView;
};

export const useApplicationSaveFlow = ({
  boardController,
  clearSelectionAfterSave,
  modalController,
  saveApplicationMutation,
  view,
}: UseApplicationSaveFlowParams) => {
  const handleSaveModal = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();

      if (
        !modalController.formMode ||
        !modalController.form.companyName.trim() ||
        !modalController.form.positionTitle.trim() ||
        !modalController.interviewsLoadedForEdit
      ) {
        return;
      }

      modalController.markSaveStarted();

      try {
        if (modalController.isCreateMode) {
          await saveApplicationMutation.mutateAsync({
            form: modalController.form,
            mode: "create",
            rows: modalController.normalizedRows,
          });
        }

        if (modalController.isEditMode && modalController.selectedApplicationId) {
          await saveApplicationMutation.mutateAsync({
            applicationId: modalController.selectedApplicationId,
            form: modalController.form,
            mode: "edit",
            pendingUndoRowIds: modalController.pendingUndoRows.map(
              (row) => row.row.rowId,
            ),
            rows: modalController.normalizedRows,
          });
        }

        modalController.markSaveSucceeded();
        clearSelectionAfterSave();
        if (view === "board") {
          void boardController.refreshPreservingLoadedCounts();
        }
      } catch {
        // Mutation onError handles UI state side-effects.
      }
    },
    [
      boardController,
      clearSelectionAfterSave,
      modalController,
      saveApplicationMutation,
      view,
    ],
  );

  const isModalSaveDisabled =
    modalController.isSaving ||
    !modalController.form.companyName.trim() ||
    !modalController.form.positionTitle.trim() ||
    !modalController.interviewsLoadedForEdit;

  return {
    handleSaveModal,
    isModalSaveDisabled,
  };
};
