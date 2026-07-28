"use client";

import { useEffect, useMemo, useReducer, useRef } from "react";
import { Application, ApplicationInterview } from "@/lib/api";
import { toInterviewDraftRow } from "../helpers/interview-form-mappers";
import {
  ApplicationFormState,
} from "../models/application-form-model";
import { InterviewDraftRow } from "../models/interview-row-model";
import {
  ApplicationCreateMethod,
  applicationModalReducer,
  initialApplicationModalState,
} from "../state/application-modal-reducer";
import { createInterviewUndoTimers } from "../state/interview-undo-timers";

const createLocalId = (): string => {
  if (crypto?.randomUUID) {
    return crypto.randomUUID();
  }

  return `row-${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const createNewInterviewDraftRow = (): InterviewDraftRow => {
  return {
    rowId: createLocalId(),
    interviewId: null,
    type: "other",
    status: "initial",
    scheduledAt: "",
  };
};

type ApplyAiDraftOptions = {
  form: ApplicationFormState;
  rows?: InterviewDraftRow[];
  warnings?: string[];
};

export const useApplicationModalController = () => {
  const [state, dispatch] = useReducer(
    applicationModalReducer,
    initialApplicationModalState,
  );

  const undoTimersRef = useRef(
    createInterviewUndoTimers((undoId: string) => {
      dispatch({
        type: "CLEAR_UNDO_INTERVIEW_ROW",
        undoId,
      });
    }),
  );

  useEffect(() => {
    const undoTimers = undoTimersRef.current;

    return () => {
      undoTimers.clearAll();
    };
  }, []);

  const normalizedRows = useMemo(
    () => state.draftInterviewRows,
    [state.draftInterviewRows],
  );

  const interviewsLoadedForEdit =
    state.mode !== "edit" ||
    (state.hasLoadedInterviews &&
      !state.isInterviewsLoading &&
      state.interviewsError === null);

  const openCreateModal = () => {
    undoTimersRef.current.clearAll();
    dispatch({ type: "OPEN_CREATE" });
  };

  const openEditModal = (application: Application) => {
    undoTimersRef.current.clearAll();
    dispatch({ type: "OPEN_EDIT", application });
  };

  const closeApplicationModal = () => {
    undoTimersRef.current.clearAll();
    dispatch({ type: "CLOSE_MODAL" });
  };

  const setCreateMethod = (method: ApplicationCreateMethod) => {
    dispatch({
      type: "SET_CREATE_METHOD",
      method,
    });
  };

  const applyAiDraft = ({
    form,
    rows = [],
    warnings = [],
  }: ApplyAiDraftOptions) => {
    dispatch({
      type: "APPLY_AI_DRAFT",
      form,
      rows,
      warnings,
    });
  };

  const markInterviewsLoading = () => {
    undoTimersRef.current.clearAll();
    dispatch({ type: "INTERVIEWS_LOADING" });
  };

  const markInterviewsLoaded = (interviews: ApplicationInterview[]) => {
    undoTimersRef.current.clearAll();
    dispatch({
      type: "INTERVIEWS_LOADED",
      rows: interviews.map(toInterviewDraftRow),
    });
  };

  const markInterviewsFailed = (error: string) => {
    dispatch({
      type: "INTERVIEWS_FAILED",
      error,
    });
  };

  const updateFormField = <Key extends keyof ApplicationFormState>(
    key: Key,
    value: ApplicationFormState[Key],
  ) => {
    dispatch({
      type: "UPDATE_APPLICATION_FIELD",
      key,
      value,
    });
  };

  const updateRow = <Key extends keyof InterviewDraftRow>(
    rowId: string,
    key: Key,
    value: InterviewDraftRow[Key],
  ) => {
    dispatch({
      type: "UPDATE_INTERVIEW_ROW",
      rowId,
      key,
      value,
    });
  };

  const addRow = () => {
    dispatch({
      type: "ADD_INTERVIEW_ROW",
      row: createNewInterviewDraftRow(),
    });
  };

  const removeRow = (rowId: string) => {
    const undoId = createLocalId();
    dispatch({
      type: "DELETE_INTERVIEW_ROW",
      rowId,
      undoId,
    });
    undoTimersRef.current.schedule(undoId);
  };

  const undoRowRemoval = (undoId: string) => {
    undoTimersRef.current.clear(undoId);
    dispatch({
      type: "UNDO_DELETE_INTERVIEW_ROW",
      undoId,
    });
  };

  const markSaveStarted = () => {
    dispatch({ type: "SAVE_STARTED" });
  };

  const markSaveFailed = (error: string) => {
    dispatch({
      type: "SAVE_FAILED",
      error,
    });
  };

  const markSaveSucceeded = () => {
    undoTimersRef.current.clearAll();
    dispatch({ type: "SAVE_SUCCEEDED" });
  };

  return {
    addRow,
    applyAiDraft,
    closeApplicationModal,
    createMethod: state.createMethod,
    draftInterviewRows: state.draftInterviewRows,
    form: state.form,
    formMode: state.mode,
    hasGeneratedDraft: state.hasGeneratedDraft,
    draftWarnings: state.draftWarnings,
    initialInterviewRows: state.initialInterviewRows,
    interviewsError: state.interviewsError,
    interviewsLoadedForEdit,
    isApplicationModalOpen: state.mode !== null,
    isCreateMode: state.mode === "create",
    isEditMode: state.mode === "edit",
    isInterviewsLoading: state.isInterviewsLoading,
    isSaving: state.isSaving,
    markInterviewsFailed,
    markInterviewsLoaded,
    markInterviewsLoading,
    markSaveFailed,
    markSaveStarted,
    markSaveSucceeded,
    normalizedRows,
    openCreateModal,
    openEditModal,
    pendingUndoRows: state.pendingUndoRows,
    removeRow,
    saveError: state.saveError && state.saveError.trim() !== "" ? state.saveError : null,
    setCreateMethod,
    selectedApplicationId: state.selectedApplicationId,
    undoRowRemoval,
    updateFormField,
    updateRow,
    deletedInterviewRows: state.deletedInterviewRows,
    hasLoadedInterviews: state.hasLoadedInterviews,
  };
};
