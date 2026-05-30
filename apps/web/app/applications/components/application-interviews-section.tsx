"use client";

import { buttonStyles, formStyles, sectionStyles, textStyles } from "@/lib/styles";
import { InterviewDraftRow } from "../models/interview-row-model";
import { InterviewRows } from "./interview-rows";

export type PendingUndoRow = {
  id: string;
  index: number;
};

export type ApplicationInterviewsSectionProps = {
  disabled: boolean;
  hasInvalidRow: boolean;
  interviewsErrorMessage: string | null;
  isEditMode: boolean;
  isInterviewsLoading: boolean;
  pendingUndoRows: PendingUndoRow[];
  rows: InterviewDraftRow[];
  onAddRow: () => void;
  onRemoveRow: (rowId: string) => void;
  onRetryInterviews: () => void;
  onUndoRemoval: (undoId: string) => void;
  onUpdateRow: <Key extends keyof InterviewDraftRow>(
    rowId: string,
    key: Key,
    value: InterviewDraftRow[Key],
  ) => void;
};

export const ApplicationInterviewsSection = ({
  disabled,
  hasInvalidRow,
  interviewsErrorMessage,
  isEditMode,
  isInterviewsLoading,
  pendingUndoRows,
  rows,
  onAddRow,
  onRemoveRow,
  onRetryInterviews,
  onUndoRemoval,
  onUpdateRow,
}: ApplicationInterviewsSectionProps) => {
  return (
    <>
      {isEditMode && isInterviewsLoading && (
        <p className={textStyles.muted}>Loading interviews...</p>
      )}

      {isEditMode && interviewsErrorMessage && (
        <div className={sectionStyles.softPanel}>
          <div className={formStyles.error}>{interviewsErrorMessage}</div>
          <button
            className={buttonStyles.secondarySoftWithTopMargin}
            onClick={onRetryInterviews}
            type="button"
          >
            Retry interviews
          </button>
        </div>
      )}

      <InterviewRows
        disabled={disabled}
        hasInvalidRow={hasInvalidRow}
        pendingUndoRows={pendingUndoRows}
        rows={rows}
        onAddRow={onAddRow}
        onRemoveRow={onRemoveRow}
        onUndoRemoval={onUndoRemoval}
        onUpdateRow={onUpdateRow}
      />
    </>
  );
};
