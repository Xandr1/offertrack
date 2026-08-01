"use client";

import { useDroppable } from "@dnd-kit/core";
import type { Application, ApplicationBoardColumn as BoardColumn } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { applicationStageLabels } from "../helpers/application-labels";
import type { BoardLoadMoreState } from "../hooks/use-applications-board-controller";
import { ApplicationBoardCard } from "./application-board-card";

type ApplicationBoardColumnProps = {
  column: BoardColumn;
  deletingApplicationId?: string;
  isStageUpdatePending: boolean;
  loadState: BoardLoadMoreState[BoardColumn["stage"]];
  onDelete: (application: Application) => void;
  onEdit: (application: Application) => void;
  onLoadMore: (stage: BoardColumn["stage"]) => void;
};

export const ApplicationBoardColumn = ({
  column,
  deletingApplicationId,
  isStageUpdatePending,
  loadState,
  onDelete,
  onEdit,
  onLoadMore,
}: ApplicationBoardColumnProps) => {
  const { isOver, setNodeRef } = useDroppable({ id: column.stage });
  const dragDisabled =
    isStageUpdatePending || Boolean(deletingApplicationId);
  const deleteDisabled = dragDisabled;

  return (
    <section
      className={`flex w-[272px] shrink-0 flex-col rounded-xl border p-2.5 xl:w-auto xl:min-w-[272px] xl:flex-1 ${
        isOver
          ? "border-violet-400 bg-violet-50/70 ring-2 ring-violet-100"
          : "border-zinc-200 bg-zinc-50"
      }`}
      ref={setNodeRef}
    >
      <header className="mb-2 px-1">
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

      <div className="flex min-h-20 flex-col gap-2">
        {column.items.length === 0 && (
          <EmptyState
            compact
            description="Drag an application here when it reaches this stage."
            title="No applications"
          />
        )}
        {column.items.map((application) => (
          <ApplicationBoardCard
            application={application}
            deleteDisabled={deleteDisabled}
            dragDisabled={dragDisabled}
            key={application.id}
            openDisabled={deletingApplicationId === application.id}
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
          className="mt-2 w-full border-transparent bg-transparent"
          disabled={loadState?.isLoading || dragDisabled}
          onClick={() => onLoadMore(column.stage)}
          variant="secondarySoft"
        >
          {loadState?.isLoading ? "Loading..." : "Load more"}
        </Button>
      )}
    </section>
  );
};
