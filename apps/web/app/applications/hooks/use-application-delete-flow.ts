"use client";

import { useCallback, useState } from "react";
import type { Application } from "@/lib/api";
import type { ApplicationsView } from "../helpers/application-filters";
import type { useApplicationMutations } from "./use-application-mutations";
import type { useApplicationsBoardController } from "./use-applications-board-controller";

type ApplicationsBoardController = ReturnType<typeof useApplicationsBoardController>;
type DeleteApplicationMutation =
  ReturnType<typeof useApplicationMutations>["deleteApplicationMutation"];

type UseApplicationDeleteFlowParams = {
  boardController: ApplicationsBoardController;
  clearPageError: () => void;
  closeSelectedApplicationAfterDelete: (applicationId: string) => void;
  deleteApplicationMutation: DeleteApplicationMutation;
  view: ApplicationsView;
};

export const useApplicationDeleteFlow = ({
  boardController,
  clearPageError,
  closeSelectedApplicationAfterDelete,
  deleteApplicationMutation,
  view,
}: UseApplicationDeleteFlowParams) => {
  const [applicationToDelete, setApplicationToDelete] =
    useState<Application | null>(null);
  const [deletingApplicationId, setDeletingApplicationId] =
    useState<string | null>(null);

  const handleDeleteRequest = useCallback(
    (application: Application) => {
      if (deletingApplicationId || deleteApplicationMutation.isPending) {
        return;
      }

      clearPageError();
      setApplicationToDelete(application);
    },
    [
      clearPageError,
      deleteApplicationMutation.isPending,
      deletingApplicationId,
    ],
  );

  const handleDeleteConfirm = useCallback(async () => {
    if (
      !applicationToDelete ||
      deletingApplicationId ||
      deleteApplicationMutation.isPending
    ) {
      return;
    }

    const applicationId = applicationToDelete.id;
    setApplicationToDelete(null);
    setDeletingApplicationId(applicationId);
    clearPageError();
    closeSelectedApplicationAfterDelete(applicationId);

    try {
      await deleteApplicationMutation.mutateAsync({ applicationId });
      if (view === "board") {
        boardController.removeApplication(applicationId);
        await boardController.refreshPreservingLoadedCounts();
      }
    } catch {
      // Mutation onError handles page-level error state.
    } finally {
      setDeletingApplicationId((currentId) =>
        currentId === applicationId ? null : currentId,
      );
    }
  }, [
    applicationToDelete,
    boardController,
    clearPageError,
    closeSelectedApplicationAfterDelete,
    deleteApplicationMutation,
    deletingApplicationId,
    view,
  ]);

  return {
    applicationToDelete,
    deletingApplicationId,
    handleDeleteConfirm,
    handleDeleteRequest,
    setApplicationToDelete,
  };
};
