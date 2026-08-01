"use client";

import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import type { DragEndEvent } from "@dnd-kit/core";
import type { Application, ApplicationBoard } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { formStyles, textStyles } from "@/lib/styles";
import type { BoardLoadMoreState } from "../hooks/use-applications-board-controller";
import { ApplicationBoardColumn } from "./application-board-column";

type ApplicationsBoardProps = {
  board?: ApplicationBoard;
  deletingApplicationId?: string;
  errorMessage: string | null;
  isLoading: boolean;
  isStageUpdatePending: boolean;
  loadMoreState: BoardLoadMoreState;
  onDragEnd: (event: DragEndEvent) => void;
  onDelete: (application: Application) => void;
  onEdit: (application: Application) => void;
  onLoadMore: (stage: ApplicationBoard["columns"][number]["stage"]) => void;
  onRetry: () => void;
};

export const ApplicationsBoard = ({
  board,
  deletingApplicationId,
  errorMessage,
  isLoading,
  isStageUpdatePending,
  loadMoreState,
  onDragEnd,
  onDelete,
  onEdit,
  onLoadMore,
  onRetry,
}: ApplicationsBoardProps) => {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor),
  );

  if (isLoading) {
    return (
      <Card variant="soft">
        <p className={textStyles.muted}>Loading board...</p>
      </Card>
    );
  }

  if (errorMessage || !board) {
    return (
      <Card variant="soft">
        <div className={formStyles.error}>
          {errorMessage ?? "Unable to load the board."}
        </div>
        <Button className="mt-2" onClick={onRetry} variant="secondarySoft">
          Retry
        </Button>
      </Card>
    );
  }

  return (
    <DndContext
      collisionDetection={closestCenter}
      sensors={sensors}
      onDragEnd={onDragEnd}
    >
      <div className="flex min-w-0 max-w-full gap-3 overflow-x-auto pb-3">
        {board.columns.map((column) => (
          <ApplicationBoardColumn
            column={column}
            deletingApplicationId={deletingApplicationId}
            isStageUpdatePending={isStageUpdatePending}
            key={column.stage}
            loadState={loadMoreState[column.stage]}
            onDelete={onDelete}
            onEdit={onEdit}
            onLoadMore={onLoadMore}
          />
        ))}
      </div>
    </DndContext>
  );
};
