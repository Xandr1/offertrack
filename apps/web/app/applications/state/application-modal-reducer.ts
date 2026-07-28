import { Application } from "@/lib/api";
import { toFormState } from "../helpers/application-form-mappers";
import {
  ApplicationFormMode,
  ApplicationFormState,
  initialApplicationFormState,
} from "../models/application-form-model";
import {
  InterviewDraftRow,
  MAX_INTERVIEW_ROWS,
} from "../models/interview-row-model";

export type PendingUndoInterviewRow = {
  id: string;
  index: number;
  row: InterviewDraftRow;
};

export type ApplicationCreateMethod = "ai" | "manual";

export type ApplicationModalState = {
  mode: ApplicationFormMode | null;
  createMethod: ApplicationCreateMethod | null;
  hasGeneratedDraft: boolean;
  selectedApplicationId: string | null;
  form: ApplicationFormState;
  initialInterviewRows: InterviewDraftRow[];
  draftInterviewRows: InterviewDraftRow[];
  rowOrderByRowId: Record<string, number>;
  pendingUndoRows: PendingUndoInterviewRow[];
  deletedInterviewRows: InterviewDraftRow[];
  hasLoadedInterviews: boolean;
  isInterviewsLoading: boolean;
  interviewsError: string | null;
  draftWarnings: string[];
  isSaving: boolean;
  saveError: string | null;
};

export const initialApplicationModalState: ApplicationModalState = {
  mode: null,
  createMethod: null,
  hasGeneratedDraft: false,
  selectedApplicationId: null,
  form: initialApplicationFormState,
  initialInterviewRows: [],
  draftInterviewRows: [],
  rowOrderByRowId: {},
  pendingUndoRows: [],
  deletedInterviewRows: [],
  hasLoadedInterviews: false,
  isInterviewsLoading: false,
  interviewsError: null,
  draftWarnings: [],
  isSaving: false,
  saveError: null,
};

type ApplicationModalAction =
  | { type: "OPEN_CREATE" }
  | { type: "OPEN_EDIT"; application: Application }
  | { type: "CLOSE_MODAL" }
  | { type: "SET_CREATE_METHOD"; method: ApplicationCreateMethod }
  | {
      type: "APPLY_AI_DRAFT";
      form: ApplicationFormState;
      rows: InterviewDraftRow[];
      warnings: string[];
    }
  | { type: "INTERVIEWS_LOADING" }
  | { type: "INTERVIEWS_LOADED"; rows: InterviewDraftRow[] }
  | { type: "INTERVIEWS_FAILED"; error: string }
  | {
      type: "UPDATE_APPLICATION_FIELD";
      key: keyof ApplicationFormState;
      value: ApplicationFormState[keyof ApplicationFormState];
    }
  | {
      type: "UPDATE_INTERVIEW_ROW";
      rowId: string;
      key: keyof InterviewDraftRow;
      value: InterviewDraftRow[keyof InterviewDraftRow];
    }
  | { type: "ADD_INTERVIEW_ROW"; row: InterviewDraftRow }
  | { type: "DELETE_INTERVIEW_ROW"; rowId: string; undoId: string }
  | { type: "UNDO_DELETE_INTERVIEW_ROW"; undoId: string }
  | { type: "CLEAR_UNDO_INTERVIEW_ROW"; undoId: string }
  | { type: "SAVE_STARTED" }
  | { type: "SAVE_FAILED"; error: string }
  | { type: "SAVE_SUCCEEDED" };

const appendDeletedInterviewRow = (
  rows: InterviewDraftRow[],
  row: InterviewDraftRow,
): InterviewDraftRow[] => {
  if (!row.interviewId) {
    return rows;
  }

  if (rows.some((candidate) => candidate.interviewId === row.interviewId)) {
    return rows;
  }

  return [...rows, row];
};

const getOccupiedInterviewSlots = (state: ApplicationModalState): number => {
  return state.draftInterviewRows.length + state.pendingUndoRows.length;
};

const buildRowOrderByRowId = (
  rows: InterviewDraftRow[],
): Record<string, number> => {
  const rowOrderByRowId: Record<string, number> = {};

  rows.forEach((row, index) => {
    rowOrderByRowId[row.rowId] = index;
  });

  return rowOrderByRowId;
};

const getNextRowOrder = (rowOrderByRowId: Record<string, number>): number => {
  const orders = Object.values(rowOrderByRowId);
  if (orders.length === 0) {
    return 0;
  }

  return Math.max(...orders) + 1;
};

const mergeDraftIntoEmptyFormFields = (
  current: ApplicationFormState,
  draft: ApplicationFormState,
): ApplicationFormState => ({
  appliedAt: current.appliedAt.trim() ? current.appliedAt : draft.appliedAt,
  companyName: current.companyName.trim()
    ? current.companyName
    : draft.companyName,
  jobUrl: current.jobUrl.trim() ? current.jobUrl : draft.jobUrl,
  location: current.location.trim() ? current.location : draft.location,
  notes: current.notes.trim() ? current.notes : draft.notes,
  positionTitle: current.positionTitle.trim()
    ? current.positionTitle
    : draft.positionTitle,
  stage: current.stage !== "initial" ? current.stage : draft.stage,
  workMode: current.workMode || draft.workMode,
});

const resolveUndoInsertIndex = (
  rows: InterviewDraftRow[],
  rowOrderByRowId: Record<string, number>,
  pendingUndoRow: PendingUndoInterviewRow,
): number => {
  const insertIndex = rows.findIndex((row) => {
    const rowOrder = rowOrderByRowId[row.rowId];
    return rowOrder > pendingUndoRow.index;
  });

  if (insertIndex === -1) {
    return rows.length;
  }

  return insertIndex;
};

export const applicationModalReducer = (
  state: ApplicationModalState,
  action: ApplicationModalAction,
): ApplicationModalState => {
  switch (action.type) {
    case "OPEN_CREATE":
      return {
        ...initialApplicationModalState,
        mode: "create",
        createMethod: "ai",
      };
    case "OPEN_EDIT":
      return {
        ...initialApplicationModalState,
        mode: "edit",
        selectedApplicationId: action.application.id,
        form: toFormState(action.application),
        isInterviewsLoading: true,
      };
    case "CLOSE_MODAL":
      return initialApplicationModalState;
    case "SET_CREATE_METHOD":
      if (state.mode !== "create") {
        return state;
      }

      return {
        ...state,
        createMethod: action.method,
      };
    case "APPLY_AI_DRAFT": {
      if (state.mode !== "create") {
        return state;
      }

      const rows =
        state.draftInterviewRows.length > 0 || state.pendingUndoRows.length > 0
          ? state.draftInterviewRows
          : action.rows;

      return {
        ...state,
        createMethod: "ai",
        hasGeneratedDraft: true,
        form: mergeDraftIntoEmptyFormFields(state.form, action.form),
        initialInterviewRows: rows,
        draftInterviewRows: rows,
        rowOrderByRowId: buildRowOrderByRowId(rows),
        draftWarnings: action.warnings,
      };
    }
    case "INTERVIEWS_LOADING":
      if (state.mode !== "edit") {
        return state;
      }

      return {
        ...state,
        hasLoadedInterviews: false,
        isInterviewsLoading: true,
        interviewsError: null,
        initialInterviewRows: [],
        draftInterviewRows: [],
        rowOrderByRowId: {},
        pendingUndoRows: [],
        deletedInterviewRows: [],
      };
    case "INTERVIEWS_LOADED":
      return {
        ...state,
        initialInterviewRows: action.rows,
        draftInterviewRows: action.rows,
        rowOrderByRowId: buildRowOrderByRowId(action.rows),
        pendingUndoRows: [],
        deletedInterviewRows: [],
        hasLoadedInterviews: true,
        isInterviewsLoading: false,
        interviewsError: null,
      };
    case "INTERVIEWS_FAILED":
      return {
        ...state,
        hasLoadedInterviews: false,
        isInterviewsLoading: false,
        interviewsError: action.error,
      };
    case "UPDATE_APPLICATION_FIELD":
      return {
        ...state,
        form: {
          ...state.form,
          [action.key]: action.value,
        },
      };
    case "UPDATE_INTERVIEW_ROW":
      return {
        ...state,
        draftInterviewRows: state.draftInterviewRows.map((row) =>
          row.rowId === action.rowId
            ? {
                ...row,
                [action.key]: action.value,
              }
            : row,
        ),
      };
    case "ADD_INTERVIEW_ROW":
      if (getOccupiedInterviewSlots(state) >= MAX_INTERVIEW_ROWS) {
        return state;
      }

      const rowOrder = getNextRowOrder(state.rowOrderByRowId);

      return {
        ...state,
        draftInterviewRows: [...state.draftInterviewRows, action.row],
        rowOrderByRowId: {
          ...state.rowOrderByRowId,
          [action.row.rowId]: rowOrder,
        },
      };
    case "DELETE_INTERVIEW_ROW": {
      const index = state.draftInterviewRows.findIndex(
        (row) => row.rowId === action.rowId,
      );
      if (index === -1) {
        return state;
      }

      const row = state.draftInterviewRows[index];
      const rowOrder = state.rowOrderByRowId[row.rowId];

      return {
        ...state,
        draftInterviewRows: state.draftInterviewRows.filter(
          (candidate) => candidate.rowId !== action.rowId,
        ),
        pendingUndoRows: [
          ...state.pendingUndoRows,
          {
            id: action.undoId,
            index: rowOrder ?? index,
            row,
          },
        ],
        deletedInterviewRows: appendDeletedInterviewRow(state.deletedInterviewRows, row),
      };
    }
    case "UNDO_DELETE_INTERVIEW_ROW": {
      const pendingUndoRow = state.pendingUndoRows.find(
        (row) => row.id === action.undoId,
      );
      if (!pendingUndoRow) {
        return state;
      }

      const nextRows = [...state.draftInterviewRows];
      const insertIndex = resolveUndoInsertIndex(
        nextRows,
        state.rowOrderByRowId,
        pendingUndoRow,
      );
      nextRows.splice(insertIndex, 0, pendingUndoRow.row);

      return {
        ...state,
        draftInterviewRows: nextRows,
        pendingUndoRows: state.pendingUndoRows.filter(
          (row) => row.id !== action.undoId,
        ),
        deletedInterviewRows: pendingUndoRow.row.interviewId
          ? state.deletedInterviewRows.filter(
              (row) => row.interviewId !== pendingUndoRow.row.interviewId,
            )
          : state.deletedInterviewRows,
      };
    }
    case "CLEAR_UNDO_INTERVIEW_ROW": {
      const pendingUndoRow = state.pendingUndoRows.find(
        (row) => row.id === action.undoId,
      );
      if (!pendingUndoRow) {
        return state;
      }

      const deletedRowId = pendingUndoRow.row.rowId;
      const deletedRowOrder = state.rowOrderByRowId[deletedRowId] ?? pendingUndoRow.index;
      const nextRowOrderByRowId: Record<string, number> = {};

      Object.entries(state.rowOrderByRowId).forEach(([rowId, rowOrder]) => {
        if (rowId === deletedRowId) {
          return;
        }

        nextRowOrderByRowId[rowId] =
          rowOrder > deletedRowOrder ? rowOrder - 1 : rowOrder;
      });

      return {
        ...state,
        pendingUndoRows: state.pendingUndoRows
          .filter((row) => row.id !== action.undoId)
          .map((row) =>
            row.index > deletedRowOrder
              ? {
                  ...row,
                  index: row.index - 1,
                }
              : row,
          ),
        rowOrderByRowId: nextRowOrderByRowId,
        deletedInterviewRows: appendDeletedInterviewRow(
          state.deletedInterviewRows,
          pendingUndoRow.row,
        ),
      };
    }
    case "SAVE_STARTED":
      return {
        ...state,
        isSaving: true,
        saveError: null,
      };
    case "SAVE_FAILED":
      return {
        ...state,
        isSaving: false,
        saveError: action.error,
      };
    case "SAVE_SUCCEEDED":
      return initialApplicationModalState;
    default:
      return state;
  }
};
