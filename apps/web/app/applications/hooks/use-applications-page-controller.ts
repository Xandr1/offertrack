"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { getErrorMessage } from "@/lib/api";
import {
  getRequestErrorMessage,
  isAuthError,
  redirectToLoginIfProtectedRoute,
} from "@/lib/request-errors";
import { useApplicationDeleteFlow } from "./use-application-delete-flow";
import { useApplicationInterviewsModalLoader } from "./use-application-interviews-modal-loader";
import { useApplicationInterviewsQuery } from "./use-application-interviews-query";
import { useApplicationModalController } from "./use-application-modal-controller";
import { useApplicationMutations } from "./use-application-mutations";
import { useApplicationQuery } from "./use-application-query";
import { useApplicationRowActions } from "./use-application-row-actions";
import { useApplicationSave } from "./use-application-save";
import { useApplicationSaveFlow } from "./use-application-save-flow";
import { useApplicationSelectionFlow } from "./use-application-selection-flow";
import { useApplicationsBoardController } from "./use-applications-board-controller";
import { useApplicationsQuery } from "./use-applications-query";
import { useApplicationsUrlFilters } from "./use-applications-url-filters";
import { useCreateApplicationWithAiFlow } from "./use-create-application-with-ai-flow";
import { useInterviewMutations } from "./use-interview-mutations";

export const useApplicationsPageController = () => {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [pageError, setPageError] = useState<string | null>(null);

  const {
    clearPageParam,
    clearSelectedApplicationId,
    direction,
    hasInvalidPageParam,
    isViewInitialized,
    listParams,
    page,
    searchInput,
    searchQuery,
    selectedApplicationId,
    setFilters,
    setPage,
    setSearchInput,
    setSelectedApplicationId,
    setView,
    sort,
    stageFilter,
    view,
  } = useApplicationsUrlFilters();
  const modalController = useApplicationModalController();
  const applicationsQuery = useApplicationsQuery(
    listParams,
    isViewInitialized && view === "list",
  );
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

    void redirectToLoginIfProtectedRoute(
      applicationsQuery.error,
      router,
      queryClient,
    );
  }, [applicationsQuery.error, queryClient, router]);

  useEffect(() => {
    if (view !== "list" || !applicationsQuery.data) {
      return;
    }

    const totalPages = applicationsQuery.data.totalPages;
    const isOutOfRangePage = totalPages > 0 && page >= totalPages;
    const isNonDefaultEmptyPage = totalPages === 0 && page > 0;

    if (!hasInvalidPageParam && !isOutOfRangePage && !isNonDefaultEmptyPage) {
      return;
    }

    clearPageParam();
  }, [applicationsQuery.data, clearPageParam, hasInvalidPageParam, page, view]);

  useEffect(() => {
    if (!applicationDetailQuery.error) {
      return;
    }

    void redirectToLoginIfProtectedRoute(
      applicationDetailQuery.error,
      router,
      queryClient,
    );
  }, [applicationDetailQuery.error, queryClient, router]);

  const {
    applicationDetailStatusKind,
    applicationDetailStatusMessage,
    clearSelectionAfterSave,
    closeApplicationModal,
    closeSelectedApplicationAfterDelete,
    openEditApplicationModal,
    prepareCreateApplicationModal,
  } = useApplicationSelectionFlow({
    applicationDetailQuery,
    clearPageError,
    clearSelectedApplicationId,
    modalController,
    selectedApplicationId,
    setSelectedApplicationId,
  });

  useApplicationInterviewsModalLoader({
    interviewsQuery,
    modalController,
  });

  const onPageMutationError = useCallback(
    async (error: unknown) => {
      if (await redirectToLoginIfProtectedRoute(error, router, queryClient)) {
        return;
      }

      setPageError(getRequestErrorMessage(error));
    },
    [queryClient, router],
  );

  const onSaveMutationError = useCallback(
    async (error: unknown) => {
      if (await redirectToLoginIfProtectedRoute(error, router, queryClient)) {
        return;
      }

      modalController.markSaveFailed(getRequestErrorMessage(error));
    },
    [modalController, queryClient, router],
  );

  const boardController = useApplicationsBoardController({
    enabled: isViewInitialized && view === "board",
    search: searchQuery,
    stage: stageFilter === "all" ? null : stageFilter,
    sort,
    direction,
    onMutationError: onPageMutationError,
  });

  useEffect(() => {
    if (!boardController.boardQuery.error) {
      return;
    }

    void redirectToLoginIfProtectedRoute(
      boardController.boardQuery.error,
      router,
      queryClient,
    );
  }, [boardController.boardQuery.error, queryClient, router]);

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
    prepareCreateApplicationModal();
    modalController.openCreateModal();
  }, [modalController, prepareCreateApplicationModal]);

  const {
    closeCreateWithAiModal,
    createApplicationDraftMutation,
    createWithAiError,
    createWithAiJobUrl,
    handleCreateWithAiSubmit,
    isCreateWithAiGenerating,
    isCreateWithAiModalOpen,
    openCreateWithAiModal,
    setCreateWithAiJobUrl,
  } = useCreateApplicationWithAiFlow({
    clearPageError,
    modalController,
    prepareCreateApplicationModal,
  });

  const {
    applicationToDelete,
    deletingApplicationId,
    handleDeleteConfirm,
    handleDeleteRequest,
    setApplicationToDelete,
  } = useApplicationDeleteFlow({
    boardController,
    clearPageError,
    closeSelectedApplicationAfterDelete,
    deleteApplicationMutation,
    view,
  });

  const {
    handleNextInterviewStatusChange,
    handleStageChange,
    nextInterviewStatusApplicationId,
    stageUpdatingApplicationId,
  } = useApplicationRowActions({
    clearPageError,
    updateInterviewStatusMutation,
    updateStageMutation,
  });

  const { handleSaveModal, isModalSaveDisabled } = useApplicationSaveFlow({
    boardController,
    clearSelectionAfterSave,
    modalController,
    saveApplicationMutation,
    view,
  });

  const submitSearch = useCallback(() => {
    setFilters({ search: searchInput });
  }, [searchInput, setFilters]);

  const clearSearch = useCallback(() => {
    setSearchInput("");
    setFilters({ search: "" });
  }, [setFilters, setSearchInput]);

  const isListAuthError = isAuthError(applicationsQuery.error);
  const isBoardAuthError = isAuthError(boardController.boardQuery.error);

  return {
    applicationDetailQuery,
    applicationDetailStatusKind,
    applicationDetailStatusMessage,
    applications: applicationsQuery.data?.items ?? [],
    applicationToDelete,
    applicationsQuery,
    boardController,
    boardErrorMessage:
      isBoardAuthError || !boardController.boardQuery.error
        ? null
        : getErrorMessage(boardController.boardQuery.error),
    closeApplicationModal,
    closeCreateWithAiModal,
    createApplicationDraftMutation,
    createWithAiError,
    createWithAiJobUrl,
    deleteApplicationMutation,
    deletingApplicationId,
    direction,
    handleCreateWithAiSubmit,
    handleDeleteConfirm,
    handleDeleteRequest,
    handleNextInterviewStatusChange,
    handleSaveModal,
    handleStageChange,
    interviewsQuery,
    isCreateWithAiGenerating,
    isListAuthError,
    isCreateWithAiModalOpen,
    isModalSaveDisabled,
    modalController,
    nextInterviewStatusApplicationId,
    openCreateApplicationModal,
    openCreateWithAiModal,
    openEditApplicationModal,
    page: applicationsQuery.data?.page ?? page,
    pageError,
    searchInput,
    clearSearch,
    setApplicationToDelete,
    setCreateWithAiJobUrl,
    setFilters,
    setPage,
    setView,
    setSearchInput,
    submitSearch,
    sort,
    stageFilter,
    stageUpdatingApplicationId,
    totalPages: applicationsQuery.data?.totalPages ?? 0,
    view,
    updateInterviewStatusMutation,
    updateStageMutation,
    listErrorMessage:
      isListAuthError || !applicationsQuery.error
        ? null
        : getErrorMessage(applicationsQuery.error),
  };
};
