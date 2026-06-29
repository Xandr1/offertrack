import {
  getApplicationBoardColumn,
  getApplicationsBoard,
} from "@/lib/api";
import type {
  ApplicationBoard,
  ApplicationBoardColumn,
  ApplicationBoardColumnParams,
  ApplicationStage,
} from "@/lib/api";
import { appendBoardColumn } from "../state/application-board-cache";

type ApplicationsBoardRefreshDependencies = {
  getBoard: (search: string) => Promise<ApplicationBoard>;
  getColumn: (
    params: ApplicationBoardColumnParams,
  ) => Promise<ApplicationBoardColumn>;
};

type RefreshApplicationsBoardInput = {
  currentBoard?: ApplicationBoard;
  search: string;
};

const defaultDependencies: ApplicationsBoardRefreshDependencies = {
  getBoard: getApplicationsBoard,
  getColumn: getApplicationBoardColumn,
};

const refreshColumn = async (
  initialColumn: ApplicationBoardColumn,
  previouslyLoadedCount: number,
  search: string,
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
      stage: refreshedColumn.stage,
      search,
      offset,
    });
    const merged = appendBoardColumn({ columns: [refreshedColumn] }, page);
    refreshedColumn = merged.columns[0];
  }

  return refreshedColumn;
};

export const refreshApplicationsBoardPreservingLoadedCounts = async (
  { currentBoard, search }: RefreshApplicationsBoardInput,
  dependencies: ApplicationsBoardRefreshDependencies = defaultDependencies,
): Promise<ApplicationBoard> => {
  const previouslyLoadedByStage = new Map<ApplicationStage, number>(
    currentBoard?.columns.map((column) => [column.stage, column.items.length]) ?? [],
  );
  const initialBoard = await dependencies.getBoard(search);
  const columns = await Promise.all(
    initialBoard.columns.map((column) =>
      refreshColumn(
        column,
        previouslyLoadedByStage.get(column.stage) ?? column.items.length,
        search,
        dependencies.getColumn,
      ),
    ),
  );

  return { columns };
};
