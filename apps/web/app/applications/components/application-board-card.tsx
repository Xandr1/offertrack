"use client";

import { useDraggable } from "@dnd-kit/core";
import type { CSSProperties } from "react";
import type { Application } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { formatCompactDateTime } from "@/lib/date-format";
import { formatUpdatedAtRelative } from "../helpers/application-date-helpers";
import { mapInterviewTypeLabel, mapWorkModeLabel } from "../helpers/application-labels";
import { IconCalendar, IconGrip, IconPencil, IconTrash } from "./ui-icons";

type ApplicationBoardCardProps = {
  application: Application;
  dragDisabled: boolean;
  onDelete: (application: Application) => void;
  onEdit: (application: Application) => void;
};

export const ApplicationBoardCard = ({
  application,
  dragDisabled,
  onDelete,
  onEdit,
}: ApplicationBoardCardProps) => {
  const {
    attributes,
    isDragging,
    listeners,
    setActivatorNodeRef,
    setNodeRef,
    transform,
  } = useDraggable({ id: application.id, disabled: dragDisabled });
  const style: CSSProperties | undefined = transform
    ? {
      transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
      zIndex: 30,
    }
    : undefined;
  const workMode = mapWorkModeLabel(application.workMode);
  const interview = application.nextInterview ?? application.lastInterview;
  const interviewLabel = interview
    ? `${application.nextInterview ? "Next" : "Last"} interview: ${mapInterviewTypeLabel(interview.type)}${
        interview.scheduledAt
          ? ` · ${formatCompactDateTime(interview.scheduledAt)}`
          : ""
      }`
    : null;

  return (
    <div ref={setNodeRef} style={style}>
      <Card
        as="article"
        className={isDragging ? "opacity-70 shadow-xl" : "shadow-sm"}
        variant="board"
      >
        <div className="flex items-start gap-1">
          <button
            aria-label={`Open ${application.companyName} application`}
            className="min-w-0 flex-1 rounded-lg p-1 text-left outline-none transition hover:bg-zinc-50 focus-visible:ring-2 focus-visible:ring-violet-500"
            disabled={dragDisabled}
            onClick={() => onEdit(application)}
            type="button"
          >
            <h3 className="truncate text-sm font-semibold text-zinc-950">
              {application.companyName}
            </h3>
            <p className="line-clamp-2 text-sm leading-5 text-zinc-700">
              {application.positionTitle}
            </p>
            {(application.location || workMode) && (
              <p className="mt-0.5 truncate text-xs text-zinc-500">
                {[application.location, workMode].filter(Boolean).join(" · ")}
              </p>
            )}
            {interviewLabel && (
              <span className="mt-2 flex min-w-0 items-center gap-1.5 border-t border-zinc-100 pt-2 text-xs font-medium text-zinc-700">
                <IconCalendar className="h-3.5 w-3.5 shrink-0 text-violet-600" />
                <span className="truncate">{interviewLabel}</span>
              </span>
            )}
            <span className="mt-1.5 block w-full min-w-0 truncate text-[11px] text-zinc-400">
              {formatUpdatedAtRelative(application.updatedAt)}
            </span>
          </button>

          <button
            {...attributes}
            {...listeners}
            aria-disabled={dragDisabled}
            aria-label={`Move ${application.companyName} application`}
            className={`inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-zinc-500 outline-none transition hover:bg-zinc-100 focus-visible:ring-2 focus-visible:ring-violet-500 ${
              dragDisabled
                ? "cursor-not-allowed opacity-50"
                : "cursor-grab touch-none active:cursor-grabbing"
            }`}
            disabled={dragDisabled}
            ref={setActivatorNodeRef}
            type="button"
          >
            <IconGrip className="h-4 w-4" />
          </button>
        </div>

        <div className="mt-1.5 flex items-center border-t border-zinc-100 pt-1.5">
          <div className="flex items-center gap-0.5">
            <Button
              className="h-8 px-2 py-1 text-xs"
              disabled={dragDisabled}
              onClick={() => onEdit(application)}
              variant="ghost"
            >
              <IconPencil className="mr-1 h-3.5 w-3.5" />
              Edit
            </Button>
            <Button
              className="h-8 px-2 py-1 text-xs"
              disabled={dragDisabled}
              onClick={() => onDelete(application)}
              variant="ghostDanger"
            >
              <IconTrash className="mr-1 h-3.5 w-3.5" />
              Delete
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
};
