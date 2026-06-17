"use client";

import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { getErrorMessage } from "@/lib/api";
import type {
  Application,
  ApplicationStage,
  InterviewStatus,
} from "@/lib/api";
import {
  getRequestErrorMessage,
  isAuthError,
  redirectToLoginIfProtectedRoute,
} from "@/lib/request-errors";
import { useApplicationInterviewsQuery } from "./use-application-interviews-query";
import { useApplicationModalController } from "./use-application-modal-controller";
import { useApplicationMutations } from "./use-application-mutations";
import { useApplicationQuery } from "./use-application-query";
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
  const openedDetailIdRef = useRef<string | null>(null);
  const suppressedDetailIdRef = useRef<string | null>(null);

  const {
    clearPageParam,
    clearSelectedApplicationId,
    direction,
    hasInvalidPageParam,
    listParams,
    page,
    searchInput,
    selectedApplicationId,
    setFilters,
    setPage,
    setSearchInput,
    setSelectedApplicationId,
    sort,
    stageFilter,
  } = useApplicationsUrlFilters();
  const modalController = useApplicationModalController();
  const applicationsQuery = useApplicationsQuery(listParams);
  const applicationDetailQuery = useApplicationQuery(
    selectedApplicationId,
    selectedApplicationId !== null,
  );
  const interviewsQuery = useApplicationInterviewsQuery(
    modalController.selectedApplicationId,
    modalController.isApplicationModalOpen && modalController.isEditMode,
  );

  const clearPageError = useCallback(() => {
    setPageError(null);
  }, []);

  useEffect(() => {
    if (!applicationsQuery.error) {
      return;
    }

    void redirectToLoginIfProtectedRoute(applicationsQuery.error, router);
  }, [applicationsQuery.error, router]);

  useEffect(() => {
    if (!applicationsQuery.data) {
      return;
    }

    const totalPages = applicationsQuery.data.totalPages;
    const isOutOfRangePage = totalPages > 0 && page >= totalPages;
    const isNonDefaultEmptyPage = totalPages === 0 && page > 0;

    if (!hasInvalidPageParam && !isOutOfRangePage && !isNonDefaultEmptyPage) {
      return;
    }

    clearPageParam();
  }, [applicationsQuery.data, clearPageParam, hasInvalidPageParam, page]);

  useEffect(() => {
    if (!applicationDetailQuery.error) {
      return;
    }

    void redirectToLoginIfProtectedRoute(applicationDetailQuery.error, router);
  }, [applicationDetailQuery.error, router]);

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
    openedDetailIdRef.current = null;
    if (selectedApplicationId) {
      suppressedDetailIdRef.current = selectedApplicationId;
      clearSelectedApplicationId();
    }
    modalController.openCreateModal();
  }, [
    clearPageError,
    clearSelectedApplicationId,
    modalController,
    selectedApplicationId,
  ]);

  const openEditApplicationModal = useCallback(
    (application: Application) => {
      clearPageError();
      suppressedDetailIdRef.current = null;
      setSelectedApplicationId(application.id);
    },
    [clearPageError, setSelectedApplicationId],
  );

  const submitSearch = useCallback(() => {
    setFilters({ search: searchInput });
  }, [searchInput, setFilters]);

  const clearSearch = useCallback(() => {
    setSearchInput("");
    setFilters({ search: "" });
  }, [setFilters, setSearchInput]);

  const closeApplicationModal = useCallback(() => {
    if (modalController.isSaving) {
      return;
    }

    modalController.closeApplicationModal();
    openedDetailIdRef.current = null;
    if (selectedApplicationId) {
      suppressedDetailIdRef.current = selectedApplicationId;
      clearSelectedApplicationId();
    }
  }, [clearSelectedApplicationId, modalController, selectedApplicationId]);

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

    if (selectedApplicationId === applicationId) {
      openedDetailIdRef.current = null;
      suppressedDetailIdRef.current = selectedApplicationId;
      modalController.closeApplicationModal();
      clearSelectedApplicationId();
    }

    deleteApplicationMutation.mutate({ applicationId });
  }, [
    applicationToDelete,
    clearPageError,
    clearSelectedApplicationId,
    deleteApplicationMutation,
    modalController,
    selectedApplicationId,
  ]);

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
        openedDetailIdRef.current = null;
        if (selectedApplicationId) {
          suppressedDetailIdRef.current = selectedApplicationId;
          clearSelectedApplicationId();
        }
      } catch {
        // Mutation onError handles UI state side-effects.
      }
    },
    [
      clearSelectedApplicationId,
      modalController,
      saveApplicationMutation,
      selectedApplicationId,
    ],
  );

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
    applicationDetailQuery,
    applicationDetailStatusKind: applicationDetailQuery.isError ? "error" : "loading",
    applicationDetailStatusMessage,
    applications: applicationsQuery.data?.items ?? [],
    applicationToDelete,
    applicationsQuery,
    closeApplicationModal,
    deleteApplicationMutation,
    deletingApplicationId,
    direction,
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
    page: applicationsQuery.data?.page ?? page,
    pageError,
    searchInput,
    clearSearch,
    setApplicationToDelete,
    setFilters,
    setPage,
    setSearchInput,
    submitSearch,
    sort,
    stageFilter,
    stageUpdatingApplicationId,
    totalPages: applicationsQuery.data?.totalPages ?? 0,
    updateInterviewStatusMutation,
    updateStageMutation,
    listErrorMessage:
      isListAuthError || !applicationsQuery.error
        ? null
        : getErrorMessage(applicationsQuery.error),
  };
};
