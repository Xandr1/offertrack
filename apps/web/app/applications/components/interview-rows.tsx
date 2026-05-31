"use client";

import { Button } from "@/components/ui/button";
import { sectionStyles, textStyles } from "@/lib/styles";
import {
  InterviewDraftRow,
  MAX_INTERVIEW_ROWS,
} from "../models/interview-row-model";
import { InterviewRow } from "./interview-row";
import { IconPlus, IconUndoTimer } from "./ui-icons";

type PendingUndoRow = {
  id: string;
  index: number;
};

type InterviewRowsProps = {
  disabled: boolean;
  hasInvalidRow: boolean;
  pendingUndoRows: PendingUndoRow[];
  rows: InterviewDraftRow[];
  onAddRow: () => void;
  onRemoveRow: (rowId: string) => void;
  onUndoRemoval: (undoId: string) => void;
  onUpdateRow: <Key extends keyof InterviewDraftRow>(
    rowId: string,
    key: Key,
    value: InterviewDraftRow[Key],
  ) => void;
};

export const InterviewRows = ({
  disabled,
  hasInvalidRow,
  pendingUndoRows,
  rows,
  onAddRow,
  onRemoveRow,
  onUndoRemoval,
  onUpdateRow,
}: InterviewRowsProps) => {
  const occupiedSlots = rows.length + pendingUndoRows.length;
  const isAtLimit = occupiedSlots >= MAX_INTERVIEW_ROWS;
  const maxPendingSlotIndex = pendingUndoRows.reduce(
    (maxIndex, row) => Math.max(maxIndex, row.index),
    -1,
  );
  const totalSlots = Math.max(occupiedSlots, maxPendingSlotIndex + 1);
  const pendingByIndex = new Map<number, PendingUndoRow[]>();

  pendingUndoRows.forEach((pendingUndoRow) => {
    const existingRows = pendingByIndex.get(pendingUndoRow.index) ?? [];
    pendingByIndex.set(pendingUndoRow.index, [...existingRows, pendingUndoRow]);
  });

  const listItems: Array<
    | { kind: "row"; key: string; row: InterviewDraftRow }
    | { kind: "undo"; key: string; undoId: string }
  > = [];
  let rowCursor = 0;

  for (let slotIndex = 0; slotIndex < totalSlots; slotIndex += 1) {
    const pendingRowsAtSlot = pendingByIndex.get(slotIndex) ?? [];

    if (pendingRowsAtSlot.length > 0) {
      pendingRowsAtSlot.forEach((pendingUndoRow) => {
        listItems.push({
          kind: "undo",
          key: pendingUndoRow.id,
          undoId: pendingUndoRow.id,
        });
      });
      continue;
    }

    const row = rows[rowCursor];
    if (!row) {
      continue;
    }

    listItems.push({
      kind: "row",
      key: row.rowId,
      row,
    });
    rowCursor += 1;
  }

  while (rowCursor < rows.length) {
    const row = rows[rowCursor];
    listItems.push({
      kind: "row",
      key: row.rowId,
      row,
    });
    rowCursor += 1;
  }

  return (
    <section className={sectionStyles.accentPanel}>
      <div className={sectionStyles.splitRow}>
        <h3 className={textStyles.sectionTitle}>Interview rounds</h3>
        <Button
          disabled={disabled || isAtLimit}
          onClick={onAddRow}
          variant="secondarySoftAccent"
          type="button"
        >
          <IconPlus className="mr-1.5 h-4 w-4" />
          Add interview
        </Button>
      </div>

      <div className="space-y-2">
        {listItems.map((item) =>
          item.kind === "row" ? (
            <InterviewRow
              disabled={disabled}
              key={item.key}
              row={item.row}
              onDelete={onRemoveRow}
              onUpdate={onUpdateRow}
            />
          ) : (
            <div className={sectionStyles.undoRowSoft} key={item.key}>
              <div className={sectionStyles.undoContent}>
                <IconUndoTimer className="h-4 w-4 animate-spin text-violet-600" />
                <span>Interview deleted · Undo (3s)</span>
              </div>
              <div className="flex h-10 items-center justify-end">
                <Button
                  disabled={disabled}
                  onClick={() => onUndoRemoval(item.undoId)}
                  variant="textAccent"
                  type="button"
                >
                  Undo
                </Button>
              </div>
            </div>
          ),
        )}
      </div>

      {hasInvalidRow && (
        <p className={textStyles.helperError}>
          Choose a type of the interview or delete it before saving
        </p>
      )}
    </section>
  );
};
