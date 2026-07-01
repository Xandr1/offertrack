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

  const handleDeleteRequest = useCallback(
    (application: Application) => {
      clearPageError();
      setApplicationToDelete(application);
    },
    [clearPageError],
  );

  const handleDeleteConfirm = useCallback(async () => {
    if (!applicationToDelete) {
      return;
    }

    const applicationId = applicationToDelete.id;
    setApplicationToDelete(null);
    clearPageError();
    closeSelectedApplicationAfterDelete(applicationId);

    try {
      await deleteApplicationMutation.mutateAsync({ applicationId });
      if (view === "board") {
        void boardController.refreshPreservingLoadedCounts();
      }
    } catch {
      // Mutation onError handles page-level error state.
    }
  }, [
    applicationToDelete,
    boardController,
    clearPageError,
    closeSelectedApplicationAfterDelete,
    deleteApplicationMutation,
    view,
  ]);

  return {
    applicationToDelete,
    deletingApplicationId: deleteApplicationMutation.variables?.applicationId,
    handleDeleteConfirm,
    handleDeleteRequest,
    setApplicationToDelete,
  };
};
