"use client";

import { Button } from "@/components/ui/button";
import { formStyles, sectionStyles, textStyles } from "@/lib/styles";
import { InterviewDraftRow } from "../models/interview-row-model";
import { InterviewRows } from "./interview-rows";

export type PendingUndoRow = {
  id: string;
  index: number;
};

export type ApplicationInterviewsSectionProps = {
  disabled: boolean;
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
          <Button
            className="mt-2"
            onClick={onRetryInterviews}
            variant="secondarySoft"
          >
            Retry interviews
          </Button>
        </div>
      )}

      <InterviewRows
        disabled={disabled}
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
