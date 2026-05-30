"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import {
  Application,
  ApplicationStage,
  InterviewStatus,
  getErrorMessage,
} from "@/lib/api";
import {
  getRequestErrorMessage,
  isAuthError,
  redirectToLoginIfProtectedRoute,
} from "@/lib/request-errors";
import { filterAndSortApplications } from "../helpers/application-filters";
import { useApplicationInterviewsQuery } from "./use-application-interviews-query";
import { useApplicationModalController } from "./use-application-modal-controller";
import { useApplicationMutations } from "./use-application-mutations";
import { useApplicationSave } from "./use-application-save";
import { useApplicationsQuery } from "./use-applications-query";
import { useApplicationsUrlFilters } from "./use-applications-url-filters";
import { useInterviewMutations } from "./use-interview-mutations";

export const useApplicationsPageController = () => {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [pageError, setPageError] = useState<string | null>(null);
  const [applicationToDelete, setApplicationToDelete] = useState<Application | null>(
    null,
  );

  const {
    searchInput,
    searchQuery,
    setFilters,
    setSearchInput,
    sort,
    stageFilter,
  } = useApplicationsUrlFilters();
  const modalController = useApplicationModalController();
  const applicationsQuery = useApplicationsQuery();
  const interviewsQuery = useApplicationInterviewsQuery(
    modalController.selectedApplicationId,
    modalController.isApplicationModalOpen && modalController.isEditMode,
  );

  useEffect(() => {
    if (!applicationsQuery.error) {
      return;
    }

    void redirectToLoginIfProtectedRoute(applicationsQuery.error, router);
  }, [applicationsQuery.error, router]);

  useEffect(() => {
    if (
      !modalController.isApplicationModalOpen ||
      !modalController.isEditMode ||
      !modalController.selectedApplicationId
    ) {
      return;
    }

    if (
      interviewsQuery.isPending &&
      !modalController.isInterviewsLoading &&
      !modalController.hasLoadedInterviews
    ) {
      modalController.markInterviewsLoading();
      return;
    }

    if (interviewsQuery.isError) {
      const nextError = getRequestErrorMessage(interviewsQuery.error);
      if (
        modalController.interviewsError !== nextError ||
        modalController.isInterviewsLoading
      ) {
        modalController.markInterviewsFailed(nextError);
      }
      return;
    }

    if (interviewsQuery.data && !modalController.hasLoadedInterviews) {
      modalController.markInterviewsLoaded(interviewsQuery.data);
    }
  }, [
    interviewsQuery.data,
    interviewsQuery.error,
    interviewsQuery.isError,
    interviewsQuery.isPending,
    modalController,
  ]);

  const clearPageError = useCallback(() => {
    setPageError(null);
  }, []);

  const onPageMutationError = useCallback(
    async (error: unknown) => {
      if (await redirectToLoginIfProtectedRoute(error, router)) {
        return;
      }

      setPageError(getRequestErrorMessage(error));
    },
    [router],
  );

  const onSaveMutationError = useCallback(
    async (error: unknown) => {
      if (await redirectToLoginIfProtectedRoute(error, router)) {
        return;
      }

      modalController.markSaveFailed(getRequestErrorMessage(error));
    },
    [modalController, router],
  );

  const { deleteApplicationMutation, updateStageMutation } = useApplicationMutations({
    onMutationError: onPageMutationError,
    queryClient,
  });
  const { updateInterviewStatusMutation } = useInterviewMutations({
    onMutationError: onPageMutationError,
    queryClient,
  });
  const { saveApplicationMutation } = useApplicationSave({
    onMutationError: onSaveMutationError,
    queryClient,
  });

  const openCreateApplicationModal = useCallback(() => {
    clearPageError();
    modalController.openCreateModal();
  }, [clearPageError, modalController]);

  const openEditApplicationModal = useCallback(
    (application: Application) => {
      clearPageError();
      modalController.openEditModal(application);
    },
    [clearPageError, modalController],
  );

  const closeApplicationModal = useCallback(() => {
    if (modalController.isSaving) {
      return;
    }

    modalController.closeApplicationModal();
  }, [modalController]);

  const handleDeleteRequest = useCallback(
    (application: Application) => {
      clearPageError();
      setApplicationToDelete(application);
    },
    [clearPageError],
  );

  const handleDeleteConfirm = useCallback(() => {
    if (!applicationToDelete) {
      return;
    }

    const applicationId = applicationToDelete.id;
    setApplicationToDelete(null);
    clearPageError();
    deleteApplicationMutation.mutate({ applicationId });
  }, [applicationToDelete, clearPageError, deleteApplicationMutation]);

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
      if (!application.nextInterview || application.nextInterview.status === status) {
        return;
      }

      clearPageError();
      updateInterviewStatusMutation.mutate({
        applicationId: application.id,
        interviewId: application.nextInterview.id,
        status,
      });
    },
    [clearPageError, updateInterviewStatusMutation],
  );

  const handleSaveModal = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();

      if (
        !modalController.formMode ||
        !modalController.form.companyName.trim() ||
        !modalController.form.positionTitle.trim() ||
        modalController.hasInvalidRow ||
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
      } catch {
        // Mutation onError handles UI state side-effects.
      }
    },
    [modalController, saveApplicationMutation],
  );

  const filteredApplications = useMemo(() => {
    return filterAndSortApplications({
      applications: applicationsQuery.data ?? [],
      searchQuery,
      sort,
      stageFilter,
    });
  }, [applicationsQuery.data, searchQuery, sort, stageFilter]);

  const stageUpdatingApplicationId = updateStageMutation.variables?.applicationId;
  const deletingApplicationId = deleteApplicationMutation.variables?.applicationId;
  const nextInterviewStatusApplicationId =
    updateInterviewStatusMutation.variables?.applicationId;

  const isModalSaveDisabled =
    modalController.isSaving ||
    !modalController.form.companyName.trim() ||
    !modalController.form.positionTitle.trim() ||
    modalController.hasInvalidRow ||
    !modalController.interviewsLoadedForEdit;

  const isListAuthError = isAuthError(applicationsQuery.error);

  return {
    applicationToDelete,
    applicationsQuery,
    closeApplicationModal,
    deleteApplicationMutation,
    deletingApplicationId,
    filteredApplications,
    handleDeleteConfirm,
    handleDeleteRequest,
    handleNextInterviewStatusChange,
    handleSaveModal,
    handleStageChange,
    interviewsQuery,
    isListAuthError,
    isModalSaveDisabled,
    modalController,
    nextInterviewStatusApplicationId,
    openCreateApplicationModal,
    openEditApplicationModal,
    pageError,
    searchInput,
    setApplicationToDelete,
    setFilters,
    setSearchInput,
    sort,
    stageFilter,
    stageUpdatingApplicationId,
    updateInterviewStatusMutation,
    updateStageMutation,
    listErrorMessage:
      isListAuthError || !applicationsQuery.error
        ? null
        : getErrorMessage(applicationsQuery.error),
  };
};
