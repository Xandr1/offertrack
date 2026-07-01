"use client";

import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createApplicationDraft, getErrorMessage } from "@/lib/api";
import type {
  Application,
  ApplicationDraftResponse,
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
import { useApplicationsBoardController } from "./use-applications-board-controller";
import { useApplicationsUrlFilters } from "./use-applications-url-filters";
import { useInterviewMutations } from "./use-interview-mutations";
import {
  ApplicationFormState,
  initialApplicationFormState,
} from "../models/application-form-model";
import {
  InterviewDraftRow,
  MAX_INTERVIEW_ROWS,
} from "../models/interview-row-model";

const MAX_JOB_URL_LENGTH = 2048;
const explicitSchemePattern = /^[A-Za-z][A-Za-z0-9+.-]*:\/\//;
const ipv4HostPattern = /^(?:\d{1,3}\.){3}\d{1,3}(?::\d+)?$/;

const looksLikeHostPath = (value: string): boolean => {
  const hostCandidate = value.split(/[/?#]/, 1)[0];
  if (!hostCandidate || hostCandidate.includes(" ")) {
    return false;
  }

  const hostWithoutPort = hostCandidate.replace(/:\d+$/, "");
  if (hostCandidate.includes(":") && hostWithoutPort === hostCandidate) {
    return false;
  }

  return (
    hostWithoutPort.toLowerCase() === "localhost" ||
    hostWithoutPort.includes(".") ||
    ipv4HostPattern.test(hostCandidate)
  );
};

const normalizeAiJobUrlInput = (
  value: string,
): { jobUrl: string; error: null } | { jobUrl: null; error: string } => {
  const trimmedValue = value.trim();

  if (!trimmedValue) {
    return { jobUrl: null, error: "Enter a job URL." };
  }

  if (trimmedValue.length > MAX_JOB_URL_LENGTH) {
    return { jobUrl: null, error: "Job URL must be 2048 characters or fewer." };
  }

  const candidate = explicitSchemePattern.test(trimmedValue)
    ? trimmedValue
    : looksLikeHostPath(trimmedValue)
      ? `https://${trimmedValue}`
      : null;

  if (!candidate) {
    return { jobUrl: null, error: "Enter a valid http or https job URL." };
  }

  try {
    const url = new URL(candidate);
    if (!["http:", "https:"].includes(url.protocol) || !url.hostname) {
      return { jobUrl: null, error: "Enter a valid http or https job URL." };
    }

    return { jobUrl: url.toString(), error: null };
  } catch {
    return { jobUrl: null, error: "Enter a valid http or https job URL." };
  }
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

export const useApplicationsPageController = () => {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [pageError, setPageError] = useState<string | null>(null);
  const [isCreateWithAiModalOpen, setIsCreateWithAiModalOpen] = useState(false);
  const [isCreateWithAiGenerating, setIsCreateWithAiGenerating] = useState(false);
  const [createWithAiJobUrl, setCreateWithAiJobUrl] = useState("");
  const [createWithAiError, setCreateWithAiError] = useState<string | null>(null);
  const [applicationToDelete, setApplicationToDelete] = useState<Application | null>(
    null,
  );
  const activeAiDraftRequestRef = useRef(0);
  const openedDetailIdRef = useRef<string | null>(null);
  const suppressedDetailIdRef = useRef<string | null>(null);

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

    void redirectToLoginIfProtectedRoute(applicationsQuery.error, router);
  }, [applicationsQuery.error, router]);

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

    void redirectToLoginIfProtectedRoute(boardController.boardQuery.error, router);
  }, [boardController.boardQuery.error, router]);

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
  const createApplicationDraftMutation = useMutation({
    mutationFn: createApplicationDraft,
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

  const openCreateApplicationModalWithDraft = useCallback(
    (draft: ApplicationDraftResponse) => {
      clearPageError();
      openedDetailIdRef.current = null;
      if (selectedApplicationId) {
        suppressedDetailIdRef.current = selectedApplicationId;
        clearSelectedApplicationId();
      }

      modalController.openCreateModal({
        form: toInitialDraftForm(draft),
        rows: toInitialDraftRows(draft),
        warnings: draft.warnings
          .map((warning) => warning.trim())
          .filter((warning) => warning !== ""),
      });
    },
    [
      clearPageError,
      clearSelectedApplicationId,
      modalController,
      selectedApplicationId,
    ],
  );

  const openCreateWithAiModal = useCallback(() => {
    clearPageError();
    activeAiDraftRequestRef.current += 1;
    setIsCreateWithAiGenerating(false);
    setCreateWithAiError(null);
    setCreateWithAiJobUrl("");
    setIsCreateWithAiModalOpen(true);
  }, [clearPageError]);

  const closeCreateWithAiModal = useCallback(() => {
    activeAiDraftRequestRef.current += 1;
    setIsCreateWithAiModalOpen(false);
    setIsCreateWithAiGenerating(false);
    setCreateWithAiError(null);
  }, []);

  const updateCreateWithAiJobUrl = useCallback((value: string) => {
    setCreateWithAiJobUrl(value);
    setCreateWithAiError(null);
  }, []);

  const handleCreateWithAiSubmit = useCallback(
    async (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();

      const normalized = normalizeAiJobUrlInput(createWithAiJobUrl);
      if (normalized.error !== null) {
        setCreateWithAiError(normalized.error);
        return;
      }

      const jobUrl = normalized.jobUrl;
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

        setIsCreateWithAiModalOpen(false);
        setCreateWithAiJobUrl("");
        openCreateApplicationModalWithDraft(draft);
      } catch (error) {
        if (activeAiDraftRequestRef.current !== requestId) {
          return;
        }

        if (await redirectToLoginIfProtectedRoute(error, router)) {
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
      createWithAiJobUrl,
      openCreateApplicationModalWithDraft,
      router,
    ],
  );

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

  const handleDeleteConfirm = useCallback(async () => {
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
    clearSelectedApplicationId,
    deleteApplicationMutation,
    modalController,
    selectedApplicationId,
    view,
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
      const interview = application.nextInterview ?? application.lastInterview;
      if (!interview || interview.status === status) {
        return;
      }

      clearPageError();
      updateInterviewStatusMutation.mutate({
        applicationId: application.id,
        interviewId: interview.id,
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
        if (view === "board") {
          void boardController.refreshPreservingLoadedCounts();
        }
      } catch {
        // Mutation onError handles UI state side-effects.
      }
    },
    [
      clearSelectedApplicationId,
      boardController,
      modalController,
      saveApplicationMutation,
      selectedApplicationId,
      view,
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
  const isBoardAuthError = isAuthError(boardController.boardQuery.error);
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
    setCreateWithAiJobUrl: updateCreateWithAiJobUrl,
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
