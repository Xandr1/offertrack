import type {
  Application,
  ApplicationBoard,
  ApplicationBoardColumn,
  ApplicationStage,
} from "@/lib/api";

const withPagination = (
  column: ApplicationBoardColumn,
  items: Application[],
  totalCount: number,
): ApplicationBoardColumn => ({
  ...column,
  items,
  totalCount,
  nextOffset: items.length,
  hasMore: items.length < totalCount,
});

export const appendBoardColumn = (
  board: ApplicationBoard,
  page: ApplicationBoardColumn,
): ApplicationBoard => ({
  columns: board.columns.map((column) => {
    if (column.stage !== page.stage) {
      return column;
    }

    const existingIds = new Set(column.items.map((item) => item.id));
    const appendedItems = page.items.filter((item) => !existingIds.has(item.id));
    return {
      ...column,
      items: [...column.items, ...appendedItems],
      totalCount: page.totalCount,
      nextOffset: page.nextOffset,
      hasMore: page.hasMore,
    };
  }),
});

export const moveBoardApplication = (
  board: ApplicationBoard,
  applicationId: string,
  targetStage: ApplicationStage,
  stageFilter: ApplicationStage | null = null,
): ApplicationBoard => {
  const sourceColumn = board.columns.find((column) =>
    column.items.some((item) => item.id === applicationId),
  );
  const targetColumn = board.columns.find(
    (column) => column.stage === targetStage,
  );

  if (!sourceColumn || !targetColumn || sourceColumn.stage === targetStage) {
    return board;
  }

  const application = sourceColumn.items.find(
    (item) => item.id === applicationId,
  );
  if (!application) {
    return board;
  }

  return {
    columns: board.columns.map((column) => {
      if (column.stage === sourceColumn.stage) {
        return withPagination(
          column,
          column.items.filter((item) => item.id !== applicationId),
          Math.max(0, column.totalCount - 1),
        );
      }

      if (column.stage === targetStage) {
        if (stageFilter !== null && targetStage !== stageFilter) {
          return column;
        }
        const movedApplication = { ...application, stage: targetStage };
        return withPagination(
          column,
          [
            movedApplication,
            ...column.items.filter((item) => item.id !== applicationId),
          ],
          column.totalCount + 1,
        );
      }

      return column;
    }),
  };
};

export const replaceBoardApplication = (
  board: ApplicationBoard,
  application: Application,
): ApplicationBoard => ({
  columns: board.columns.map((column) => ({
    ...column,
    items: column.items.map((item) =>
      item.id === application.id ? application : item,
    ),
  })),
});
