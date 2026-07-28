import { Application } from "@/lib/api";
import { initialApplicationFormState } from "../models/application-form-model";
import { InterviewDraftRow } from "../models/interview-row-model";
import {
  applicationModalReducer,
  initialApplicationModalState,
} from "./application-modal-reducer";

const makeApplication = (): Application => ({
  appliedAt: null,
  companyName: "Acme",
  createdAt: "2026-01-01T00:00:00.000Z",
  id: "app-1",
  jobUrl: null,
  location: null,
  followedUpAt: null,
  lastInterview: null,
  nextInterview: null,
  notes: null,
  positionTitle: "Engineer",
  stage: "applied",
  updatedAt: "2026-01-01T00:00:00.000Z",
  workMode: null,
});

const makeRow = (id: string, overrides: Partial<InterviewDraftRow> = {}): InterviewDraftRow => ({
  interviewId: id,
  rowId: id,
  scheduledAt: "",
  status: "initial",
  type: "technical",
  ...overrides,
});

describe("application-modal-reducer", () => {
  it("opens create with clean draft state", () => {
    const nextState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_CREATE",
    });

    expect(nextState.mode).toBe("create");
    expect(nextState.createMethod).toBe("ai");
    expect(nextState.hasGeneratedDraft).toBe(false);
    expect(nextState.form).toEqual(initialApplicationFormState);
    expect(nextState.draftInterviewRows).toEqual([]);
    expect(nextState.pendingUndoRows).toEqual([]);
  });

  it("switches create methods without resetting entered form data", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_CREATE",
    });
    const editedState = applicationModalReducer(openState, {
      type: "UPDATE_APPLICATION_FIELD",
      key: "companyName",
      value: "Kept Company",
    });
    const manualState = applicationModalReducer(editedState, {
      type: "SET_CREATE_METHOD",
      method: "manual",
    });
    const aiState = applicationModalReducer(manualState, {
      type: "SET_CREATE_METHOD",
      method: "ai",
    });

    expect(manualState.form.companyName).toBe("Kept Company");
    expect(aiState.form.companyName).toBe("Kept Company");
    expect(aiState.createMethod).toBe("ai");
  });

  it("applies an AI draft into empty fields without overwriting manual values", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_CREATE",
    });
    const editedState = applicationModalReducer(openState, {
      type: "UPDATE_APPLICATION_FIELD",
      key: "companyName",
      value: "Kept Company",
    });
    const draftState = applicationModalReducer(editedState, {
      type: "APPLY_AI_DRAFT",
      form: {
        ...initialApplicationFormState,
        companyName: "Generated Company",
        location: "Remote",
        positionTitle: "Generated Engineer",
      },
      rows: [makeRow("draft-row", { interviewId: null, type: "recruiter" })],
      warnings: ["Location was inferred."],
    });

    expect(draftState.hasGeneratedDraft).toBe(true);
    expect(draftState.form.companyName).toBe("Kept Company");
    expect(draftState.form.positionTitle).toBe("Generated Engineer");
    expect(draftState.form.location).toBe("Remote");
    expect(draftState.draftInterviewRows[0]?.type).toBe("recruiter");
    expect(draftState.draftWarnings).toEqual(["Location was inferred."]);
  });

  it("opens edit in loading state", () => {
    const nextState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });

    expect(nextState.mode).toBe("edit");
    expect(nextState.selectedApplicationId).toBe("app-1");
    expect(nextState.isInterviewsLoading).toBe(true);
    expect(nextState.hasLoadedInterviews).toBe(false);
  });

  it("initializes interview rows on load", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });

    const loadedState = applicationModalReducer(openState, {
      type: "INTERVIEWS_LOADED",
      rows: [makeRow("int-1"), makeRow("int-2")],
    });

    expect(loadedState.initialInterviewRows).toHaveLength(2);
    expect(loadedState.draftInterviewRows).toHaveLength(2);
    expect(loadedState.hasLoadedInterviews).toBe(true);
    expect(loadedState.isInterviewsLoading).toBe(false);
  });

  it("stores interviews failure and keeps edit mode open", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });

    const failedState = applicationModalReducer(openState, {
      type: "INTERVIEWS_FAILED",
      error: "boom",
    });

    expect(failedState.mode).toBe("edit");
    expect(failedState.isInterviewsLoading).toBe(false);
    expect(failedState.interviewsError).toBe("boom");
  });

  it("resets state on close", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_CREATE",
    });

    const closedState = applicationModalReducer(openState, {
      type: "CLOSE_MODAL",
    });

    expect(closedState).toEqual(initialApplicationModalState);
  });

  it("handles save started, failed and succeeded transitions", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_CREATE",
    });

    const savingState = applicationModalReducer(openState, {
      type: "SAVE_STARTED",
    });
    expect(savingState.isSaving).toBe(true);
    expect(savingState.mode).toBe("create");

    const failedState = applicationModalReducer(savingState, {
      type: "SAVE_FAILED",
      error: "cannot save",
    });
    expect(failedState.isSaving).toBe(false);
    expect(failedState.mode).toBe("create");
    expect(failedState.saveError).toBe("cannot save");

    const successState = applicationModalReducer(failedState, {
      type: "SAVE_SUCCEEDED",
    });
    expect(successState).toEqual(initialApplicationModalState);
  });

  it("supports delete -> undo transitions", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });
    const loadedState = applicationModalReducer(openState, {
      type: "INTERVIEWS_LOADED",
      rows: [makeRow("int-1"), makeRow("int-2")],
    });

    const deletedState = applicationModalReducer(loadedState, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-1",
      undoId: "undo-1",
    });
    expect(deletedState.draftInterviewRows.map((row) => row.rowId)).toEqual(["int-2"]);
    expect(deletedState.pendingUndoRows).toHaveLength(1);
    expect(deletedState.deletedInterviewRows.map((row) => row.interviewId)).toEqual([
      "int-1",
    ]);

    const undoState = applicationModalReducer(deletedState, {
      type: "UNDO_DELETE_INTERVIEW_ROW",
      undoId: "undo-1",
    });
    expect(undoState.draftInterviewRows.map((row) => row.rowId)).toEqual([
      "int-1",
      "int-2",
    ]);
    expect(undoState.pendingUndoRows).toHaveLength(0);
    expect(undoState.deletedInterviewRows).toHaveLength(0);
  });

  it("restores middle row to the same place after undo", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });
    const loadedState = applicationModalReducer(openState, {
      type: "INTERVIEWS_LOADED",
      rows: [makeRow("int-1"), makeRow("int-2"), makeRow("int-3")],
    });

    const deletedState = applicationModalReducer(loadedState, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-2",
      undoId: "undo-middle",
    });
    expect(deletedState.draftInterviewRows.map((row) => row.rowId)).toEqual([
      "int-1",
      "int-3",
    ]);

    const undoState = applicationModalReducer(deletedState, {
      type: "UNDO_DELETE_INTERVIEW_ROW",
      undoId: "undo-middle",
    });
    expect(undoState.draftInterviewRows.map((row) => row.rowId)).toEqual([
      "int-1",
      "int-2",
      "int-3",
    ]);
  });

  it("clears undo rows after timeout transition", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });
    const loadedState = applicationModalReducer(openState, {
      type: "INTERVIEWS_LOADED",
      rows: [makeRow("int-1"), makeRow("int-2")],
    });
    const deletedState = applicationModalReducer(loadedState, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-1",
      undoId: "undo-1",
    });

    const clearedState = applicationModalReducer(deletedState, {
      type: "CLEAR_UNDO_INTERVIEW_ROW",
      undoId: "undo-1",
    });

    expect(clearedState.pendingUndoRows).toHaveLength(0);
    expect(clearedState.draftInterviewRows.map((row) => row.rowId)).toEqual(["int-2"]);
    expect(clearedState.deletedInterviewRows.map((row) => row.interviewId)).toEqual([
      "int-1",
    ]);
  });

  it("keeps deleted rows in occupied slots while undo is visible", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });
    const loadedState = applicationModalReducer(openState, {
      type: "INTERVIEWS_LOADED",
      rows: Array.from({ length: 10 }, (_value, index) =>
        makeRow(`int-${index + 1}`),
      ),
    });

    const deletedState = applicationModalReducer(loadedState, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-1",
      undoId: "undo-1",
    });

    expect(deletedState.draftInterviewRows).toHaveLength(9);
    expect(deletedState.pendingUndoRows).toHaveLength(1);

    const addAttemptState = applicationModalReducer(deletedState, {
      type: "ADD_INTERVIEW_ROW",
      row: {
        interviewId: null,
        rowId: "new-row-1",
        scheduledAt: "",
        status: "initial",
        type: "other",
      },
    });

    expect(addAttemptState.draftInterviewRows).toHaveLength(9);
    expect(addAttemptState.pendingUndoRows).toHaveLength(1);
  });

  it("at limit 10 keeps add disabled after undo of pending deletion", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });
    const loadedState = applicationModalReducer(openState, {
      type: "INTERVIEWS_LOADED",
      rows: Array.from({ length: 10 }, (_value, index) =>
        makeRow(`int-${index + 1}`),
      ),
    });

    const deletedState = applicationModalReducer(loadedState, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-1",
      undoId: "undo-1",
    });
    const undoState = applicationModalReducer(deletedState, {
      type: "UNDO_DELETE_INTERVIEW_ROW",
      undoId: "undo-1",
    });

    const addAttemptState = applicationModalReducer(undoState, {
      type: "ADD_INTERVIEW_ROW",
      row: {
        interviewId: null,
        rowId: "new-row-after-undo",
        scheduledAt: "",
        status: "initial",
        type: "other",
      },
    });

    expect(addAttemptState.draftInterviewRows).toHaveLength(10);
    expect(
      addAttemptState.draftInterviewRows.some(
        (row) => row.rowId === "new-row-after-undo",
      ),
    ).toBe(false);
  });

  it("at limit 10 allows add after pending deletion expires", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });
    const loadedState = applicationModalReducer(openState, {
      type: "INTERVIEWS_LOADED",
      rows: Array.from({ length: 10 }, (_value, index) =>
        makeRow(`int-${index + 1}`),
      ),
    });

    const deletedState = applicationModalReducer(loadedState, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-1",
      undoId: "undo-1",
    });
    const expiredState = applicationModalReducer(deletedState, {
      type: "CLEAR_UNDO_INTERVIEW_ROW",
      undoId: "undo-1",
    });

    const addAttemptState = applicationModalReducer(expiredState, {
      type: "ADD_INTERVIEW_ROW",
      row: {
        interviewId: null,
        rowId: "new-row-after-expiry",
        scheduledAt: "",
        status: "initial",
        type: "other",
      },
    });

    expect(addAttemptState.draftInterviewRows).toHaveLength(10);
    expect(
      addAttemptState.draftInterviewRows.some(
        (row) => row.rowId === "new-row-after-expiry",
      ),
    ).toBe(true);
  });

  it("restores rows in stable order after sequential deletes and undos", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });
    const loadedState = applicationModalReducer(openState, {
      type: "INTERVIEWS_LOADED",
      rows: [makeRow("int-1"), makeRow("int-2"), makeRow("int-3")],
    });

    const afterFirstDelete = applicationModalReducer(loadedState, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-1",
      undoId: "undo-1",
    });
    const afterSecondDelete = applicationModalReducer(afterFirstDelete, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-2",
      undoId: "undo-2",
    });

    const afterFirstUndo = applicationModalReducer(afterSecondDelete, {
      type: "UNDO_DELETE_INTERVIEW_ROW",
      undoId: "undo-1",
    });
    const afterSecondUndo = applicationModalReducer(afterFirstUndo, {
      type: "UNDO_DELETE_INTERVIEW_ROW",
      undoId: "undo-2",
    });

    expect(afterSecondUndo.draftInterviewRows.map((row) => row.rowId)).toEqual([
      "int-1",
      "int-2",
      "int-3",
    ]);
  });

  it("restores rows in stable order even when undo order is reversed", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });
    const loadedState = applicationModalReducer(openState, {
      type: "INTERVIEWS_LOADED",
      rows: [makeRow("int-1"), makeRow("int-2"), makeRow("int-3")],
    });

    const afterFirstDelete = applicationModalReducer(loadedState, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-1",
      undoId: "undo-1",
    });
    const afterSecondDelete = applicationModalReducer(afterFirstDelete, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-2",
      undoId: "undo-2",
    });

    const afterSecondUndoFirst = applicationModalReducer(afterSecondDelete, {
      type: "UNDO_DELETE_INTERVIEW_ROW",
      undoId: "undo-2",
    });
    const afterFirstUndoSecond = applicationModalReducer(afterSecondUndoFirst, {
      type: "UNDO_DELETE_INTERVIEW_ROW",
      undoId: "undo-1",
    });

    expect(afterFirstUndoSecond.draftInterviewRows.map((row) => row.rowId)).toEqual([
      "int-1",
      "int-2",
      "int-3",
    ]);
  });

  it("supports delete A -> delete B -> A expires -> undo B", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });
    const loadedState = applicationModalReducer(openState, {
      type: "INTERVIEWS_LOADED",
      rows: [makeRow("int-1"), makeRow("int-2"), makeRow("int-3")],
    });

    const afterDeleteA = applicationModalReducer(loadedState, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-1",
      undoId: "undo-a",
    });
    const afterDeleteB = applicationModalReducer(afterDeleteA, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-2",
      undoId: "undo-b",
    });
    const afterAExpiry = applicationModalReducer(afterDeleteB, {
      type: "CLEAR_UNDO_INTERVIEW_ROW",
      undoId: "undo-a",
    });
    const afterBUndo = applicationModalReducer(afterAExpiry, {
      type: "UNDO_DELETE_INTERVIEW_ROW",
      undoId: "undo-b",
    });

    expect(afterBUndo.draftInterviewRows.map((row) => row.rowId)).toEqual([
      "int-2",
      "int-3",
    ]);
    expect(afterBUndo.pendingUndoRows).toHaveLength(0);
  });

  it("supports delete row -> expiry -> add new row", () => {
    const openState = applicationModalReducer(initialApplicationModalState, {
      type: "OPEN_EDIT",
      application: makeApplication(),
    });
    const loadedState = applicationModalReducer(openState, {
      type: "INTERVIEWS_LOADED",
      rows: [makeRow("int-1"), makeRow("int-2"), makeRow("int-3")],
    });

    const deletedState = applicationModalReducer(loadedState, {
      type: "DELETE_INTERVIEW_ROW",
      rowId: "int-2",
      undoId: "undo-1",
    });
    const expiredState = applicationModalReducer(deletedState, {
      type: "CLEAR_UNDO_INTERVIEW_ROW",
      undoId: "undo-1",
    });
    const addedState = applicationModalReducer(expiredState, {
      type: "ADD_INTERVIEW_ROW",
      row: {
        interviewId: null,
        rowId: "new-row-after-expiry",
        scheduledAt: "",
        status: "initial",
        type: "other",
      },
    });

    expect(addedState.draftInterviewRows.map((row) => row.rowId)).toEqual([
      "int-1",
      "int-3",
      "new-row-after-expiry",
    ]);
  });
});
