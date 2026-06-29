"use client";

import { useDroppable } from "@dnd-kit/core";
import type { Application, ApplicationBoardColumn as BoardColumn } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { applicationStageLabels } from "../helpers/application-labels";
import type { BoardLoadMoreState } from "../hooks/use-applications-board-controller";
import { ApplicationBoardCard } from "./application-board-card";

type ApplicationBoardColumnProps = {
  column: BoardColumn;
  isStageUpdatePending: boolean;
  loadState: BoardLoadMoreState[BoardColumn["stage"]];
  onDelete: (application: Application) => void;
  onEdit: (application: Application) => void;
  onLoadMore: (stage: BoardColumn["stage"]) => void;
};

export const ApplicationBoardColumn = ({
  column,
  isStageUpdatePending,
  loadState,
  onDelete,
  onEdit,
  onLoadMore,
}: ApplicationBoardColumnProps) => {
  const { isOver, setNodeRef } = useDroppable({ id: column.stage });

  return (
    <section
      className={`flex w-[260px] shrink-0 flex-col rounded-2xl border p-3 xl:w-auto xl:min-w-0 xl:flex-1 ${
        isOver
          ? "border-violet-400 bg-violet-50"
          : "border-zinc-200 bg-zinc-50"
      }`}
      ref={setNodeRef}
    >
      <header className="mb-3 px-1">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-zinc-950">
            {applicationStageLabels[column.stage]}
          </h2>
          <span className="rounded-full bg-white px-2 py-0.5 text-xs font-medium text-zinc-600 shadow-sm">
            {column.totalCount}
          </span>
        </div>
        <p className="mt-1 text-xs text-zinc-500">
          Showing {column.items.length} of {column.totalCount}
        </p>
      </header>

      <div className="flex min-h-24 flex-col gap-2">
        {column.items.length === 0 && (
          <p className="rounded-xl border border-dashed border-zinc-300 px-3 py-8 text-center text-sm text-zinc-500">
            No applications
          </p>
        )}
        {column.items.map((application) => (
          <ApplicationBoardCard
            application={application}
            dragDisabled={isStageUpdatePending}
            key={application.id}
            onDelete={onDelete}
            onEdit={onEdit}
          />
        ))}
      </div>

      {loadState?.error && (
        <p className="mt-3 text-xs text-red-700">{loadState.error}</p>
      )}
      {column.hasMore && (
        <Button
          className="mt-3 w-full"
          disabled={loadState?.isLoading || isStageUpdatePending}
          onClick={() => onLoadMore(column.stage)}
          variant="secondarySoft"
        >
          {loadState?.isLoading ? "Loading..." : "Load more"}
        </Button>
      )}
    </section>
  );
};
