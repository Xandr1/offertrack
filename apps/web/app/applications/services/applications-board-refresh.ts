import {
  getApplicationBoardColumn,
  getApplicationsBoard,
} from "@/lib/api";
import type {
  ApplicationBoard,
  ApplicationBoardColumn,
  ApplicationBoardColumnParams,
  ApplicationStage,
  ApplicationsBoardParams,
} from "@/lib/api";
import { appendBoardColumn } from "../state/application-board-cache";

type ApplicationsBoardRefreshDependencies = {
  getBoard: (params: ApplicationsBoardParams) => Promise<ApplicationBoard>;
  getColumn: (
    params: ApplicationBoardColumnParams,
  ) => Promise<ApplicationBoardColumn>;
};

type RefreshApplicationsBoardInput = ApplicationsBoardParams & {
  currentBoard?: ApplicationBoard;
};

const defaultDependencies: ApplicationsBoardRefreshDependencies = {
  getBoard: getApplicationsBoard,
  getColumn: getApplicationBoardColumn,
};

const refreshColumn = async (
  initialColumn: ApplicationBoardColumn,
  previouslyLoadedCount: number,
  boardParams: ApplicationsBoardParams,
  getColumn: ApplicationsBoardRefreshDependencies["getColumn"],
): Promise<ApplicationBoardColumn> => {
  let refreshedColumn = initialColumn;
  const seenOffsets = new Set<number>();

  while (
    refreshedColumn.items.length <
      Math.min(previouslyLoadedCount, refreshedColumn.totalCount) &&
    refreshedColumn.hasMore
  ) {
    const offset = refreshedColumn.nextOffset;
    if (
      offset >= refreshedColumn.totalCount ||
      seenOffsets.has(offset)
    ) {
      break;
    }

    seenOffsets.add(offset);
    const page = await getColumn({
      columnStage: refreshedColumn.stage,
      ...boardParams,
      offset,
    });
    const merged = appendBoardColumn({ columns: [refreshedColumn] }, page);
    refreshedColumn = merged.columns[0];
    if (page.nextOffset <= offset) {
      break;
    }
  }

  return refreshedColumn;
};

export const refreshApplicationsBoardPreservingLoadedCounts = async (
  { currentBoard, search, stage, sort, direction }: RefreshApplicationsBoardInput,
  dependencies: ApplicationsBoardRefreshDependencies = defaultDependencies,
): Promise<ApplicationBoard> => {
  const previouslyLoadedByStage = new Map<ApplicationStage, number>(
    currentBoard?.columns.map((column) => [column.stage, column.items.length]) ?? [],
  );
  const boardParams = { search, stage, sort, direction };
  const initialBoard = await dependencies.getBoard(boardParams);
  const columns = await Promise.all(
    initialBoard.columns.map((column) =>
      refreshColumn(
        column,
        previouslyLoadedByStage.get(column.stage) ?? column.items.length,
        boardParams,
        dependencies.getColumn,
      ),
    ),
  );

  return { columns };
};
