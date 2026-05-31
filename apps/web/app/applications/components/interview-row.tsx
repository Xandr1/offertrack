"use client";

import { InterviewStatus, InterviewType } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/input";
import { sectionStyles } from "@/lib/styles";
import {
  interviewStatusLabels,
  interviewTypeLabels,
} from "../helpers/application-labels";
import { interviewStatuses, interviewTypes } from "../helpers/constants";
import { InterviewDraftRow } from "../models/interview-row-model";
import { IconCheckCircle, IconTrash } from "./ui-icons";

type InterviewRowProps = {
  disabled: boolean;
  row: InterviewDraftRow;
  onDelete: (rowId: string) => void;
  onUpdate: <Key extends keyof InterviewDraftRow>(
    rowId: string,
    key: Key,
    value: InterviewDraftRow[Key],
  ) => void;
};

export const InterviewRow = ({
  disabled,
  row,
  onDelete,
  onUpdate,
}: InterviewRowProps) => {
  const isPassed = row.status === "passed";

  return (
    <div className={sectionStyles.interviewRow}>
      <div className="flex items-center gap-1.5">
        <span className={sectionStyles.interviewStatusSlot} aria-hidden>
          {isPassed && (
            <IconCheckCircle className={sectionStyles.interviewStatusIcon} />
          )}
        </span>
        <Select
          disabled={disabled || isPassed}
          value={row.type}
          onChange={(event) =>
            onUpdate(row.rowId, "type", event.target.value as InterviewType | "")
          }
        >
          <option value="">Select type</option>
          {interviewTypes.map((type) => (
            <option key={type} value={type}>
              {interviewTypeLabels[type]}
            </option>
          ))}
        </Select>
      </div>

      <div>
        <Select
          disabled={disabled}
          value={row.status}
          onChange={(event) =>
            onUpdate(row.rowId, "status", event.target.value as InterviewStatus)
          }
        >
          {interviewStatuses.map((status) => (
            <option key={status} value={status}>
              {interviewStatusLabels[status]}
            </option>
          ))}
        </Select>
      </div>

      <div>
        <Input
          disabled={disabled || isPassed}
          type="datetime-local"
          value={row.scheduledAt}
          onChange={(event) => onUpdate(row.rowId, "scheduledAt", event.target.value)}
        />
      </div>

      <div className="flex justify-end">
        <Button
          aria-label="Delete interview row"
          disabled={disabled}
          onClick={() => onDelete(row.rowId)}
          variant="iconGhostDanger"
          type="button"
        >
          <IconTrash className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
};
