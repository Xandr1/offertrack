"use client";

import { useDraggable } from "@dnd-kit/core";
import type { CSSProperties } from "react";
import type { Application } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { formatCompactDateTime } from "@/lib/date-format";
import { formatUpdatedAtRelative } from "../helpers/application-date-helpers";
import { mapInterviewTypeLabel, mapWorkModeLabel } from "../helpers/application-labels";
import { IconPencil, IconTrash } from "./ui-icons";

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
  const interviewLabel = interview?.scheduledAt
    ? `${application.nextInterview ? "📅" : "Last:"} ${mapInterviewTypeLabel(interview.type)} · ${formatCompactDateTime(interview.scheduledAt)}`
    : null;

  return (
    <div ref={setNodeRef} style={style}>
      <Card
        as="article"
        className={isDragging ? "opacity-70 shadow-xl" : "shadow-sm"}
      >
        <div
          {...attributes}
          {...listeners}
          aria-disabled={dragDisabled}
          aria-label={`Move ${application.companyName} application`}
          className={`min-w-0 rounded-lg p-1 outline-none focus-visible:ring-2 focus-visible:ring-violet-400 ${dragDisabled
            ? "cursor-not-allowed"
            : "cursor-grab select-none active:cursor-grabbing"
            }`}
          ref={setActivatorNodeRef}
          tabIndex={dragDisabled ? -1 : attributes.tabIndex}
        >
          <h3 className="truncate text-sm font-semibold text-zinc-950">
            {application.companyName}
          </h3>
          <p className="truncate text-sm text-zinc-700">
            {application.positionTitle}
          </p>
          {(application.location || workMode) && (
            <p className="truncate text-sm text-zinc-500">
              {[application.location, workMode].filter(Boolean).join(" · ")}
            </p>
          )}
          {interviewLabel && (
            <p className="mt-2 truncate text-xs font-medium text-zinc-700">
              {interviewLabel}
            </p>
          )}
          <span className="min-w-0 truncate text-xs text-zinc-500">
            {formatUpdatedAtRelative(application.updatedAt)}
          </span>
        </div>

        <div className="flex items-center justify-between border-t border-zinc-100 pt-2">
          <div className="flex shrink-0 items-center gap-0.5">
            <Button
              className="px-2 py-1 text-xs"
              disabled={dragDisabled}
              onClick={() => onEdit(application)}
              variant="ghost"
            >
              <IconPencil className="mr-1 h-3.5 w-3.5" />
              Edit
            </Button>
            <Button
              className="px-2 py-1 text-xs"
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
