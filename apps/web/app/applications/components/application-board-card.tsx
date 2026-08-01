"use client";

import { useDraggable } from "@dnd-kit/core";
import { useEffect, useRef } from "react";
import type { CSSProperties } from "react";
import type { Application } from "@/lib/api";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { formatCompactDateTime } from "@/lib/date-format";
import { formatUpdatedAtRelative } from "../helpers/application-date-helpers";
import { mapInterviewTypeLabel, mapWorkModeLabel } from "../helpers/application-labels";
import { IconCalendar, IconTrash } from "./ui-icons";

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
  const suppressClickRef = useRef(false);
  const resetClickSuppressionTimerRef = useRef<ReturnType<
    typeof setTimeout
  > | null>(null);
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

  useEffect(() => {
    if (resetClickSuppressionTimerRef.current !== null) {
      clearTimeout(resetClickSuppressionTimerRef.current);
      resetClickSuppressionTimerRef.current = null;
    }

    if (isDragging) {
      suppressClickRef.current = true;
      return;
    }

    if (suppressClickRef.current) {
      resetClickSuppressionTimerRef.current = setTimeout(() => {
        suppressClickRef.current = false;
        resetClickSuppressionTimerRef.current = null;
      }, 0);
    }

    return () => {
      if (resetClickSuppressionTimerRef.current !== null) {
        clearTimeout(resetClickSuppressionTimerRef.current);
        resetClickSuppressionTimerRef.current = null;
      }
    };
  }, [isDragging]);

  return (
    <div ref={setNodeRef} style={style}>
      <Card
        as="article"
        className={`relative overflow-hidden ${
          isDragging ? "opacity-70 shadow-xl" : "shadow-sm"
        }`}
        variant="board"
      >
        <button
          {...attributes}
          {...listeners}
          aria-label={`Open ${application.companyName} application`}
          className={`block w-full touch-none rounded-xl p-3 pr-12 text-left outline-none transition hover:bg-zinc-50 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-violet-500 ${
            dragDisabled
              ? "cursor-not-allowed opacity-60"
              : isDragging
                ? "cursor-grabbing"
                : "cursor-pointer"
          }`}
          disabled={dragDisabled}
          ref={setActivatorNodeRef}
          type="button"
          onClick={(event) => {
            if (suppressClickRef.current) {
              event.preventDefault();
              event.stopPropagation();
              return;
            }

            onEdit(application);
          }}
          onKeyDown={(event) => {
            if (event.key !== "Enter") {
              listeners?.onKeyDown?.(event);
            }
          }}
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

        <Button
          aria-label={`Delete ${application.companyName} application`}
          className="absolute right-2 top-2 z-10"
          disabled={dragDisabled}
          variant="iconGhostDanger"
          onClick={(event) => {
            event.stopPropagation();
            onDelete(application);
          }}
          onPointerDown={(event) => {
            event.stopPropagation();
          }}
        >
          <IconTrash className="h-4 w-4" />
        </Button>
      </Card>
    </div>
  );
};
