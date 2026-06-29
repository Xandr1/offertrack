import type {
  Application,
  ApplicationBoard,
  ApplicationBoardColumn,
  ApplicationStage,
} from "@/lib/api";
import { refreshApplicationsBoardPreservingLoadedCounts } from "./applications-board-refresh";

const makeApplication = (
  id: string,
  stage: ApplicationStage,
): Application => ({
  appliedAt: null,
  companyName: `Company ${id}`,
  createdAt: "2026-01-01T00:00:00Z",
  id,
  jobUrl: null,
  location: null,
  nextInterview: null,
  notes: null,
  positionTitle: "Engineer",
  stage,
  updatedAt: "2026-01-01T00:00:00Z",
  workMode: null,
});

const makeItems = (
  stage: ApplicationStage,
  start: number,
  count: number,
): Application[] =>
  Array.from({ length: count }, (_, index) =>
    makeApplication(`${stage}-${start + index}`, stage),
  );

const makeColumn = (
  stage: ApplicationStage,
  items: Application[],
  totalCount: number,
  nextOffset: number,
  hasMore: boolean,
): ApplicationBoardColumn => ({
  stage,
  items,
  totalCount,
  nextOffset,
  hasMore,
});

describe("applications board refresh", () => {
  it("preserves loaded depth independently per stage using backend offsets", async () => {
    const currentBoard: ApplicationBoard = {
      columns: [
        makeColumn("initial", makeItems("initial", 0, 20), 30, 20, true),
        makeColumn("applied", makeItems("applied", 0, 60), 80, 60, true),
      ],
    };
    const getColumn = jest
      .fn()
      .mockResolvedValueOnce(
        makeColumn("applied", makeItems("applied", 20, 20), 80, 40, true),
      )
      .mockResolvedValueOnce(
        makeColumn("applied", makeItems("applied", 40, 20), 80, 60, true),
      );

    const result = await refreshApplicationsBoardPreservingLoadedCounts(
      { currentBoard, search: "acme" },
      {
        getBoard: jest.fn().mockResolvedValue({
          columns: [
            makeColumn("initial", makeItems("initial", 0, 20), 30, 20, true),
            makeColumn("applied", makeItems("applied", 0, 20), 80, 20, true),
          ],
        }),
        getColumn,
      },
    );

    expect(result.columns[0].items).toHaveLength(20);
    expect(result.columns[1].items).toHaveLength(60);
    expect(getColumn.mock.calls).toEqual([
      [{ stage: "applied", search: "acme", offset: 20 }],
      [{ stage: "applied", search: "acme", offset: 40 }],
    ]);
  });

  it("caps the target by refreshed total count", async () => {
    const currentBoard: ApplicationBoard = {
      columns: [
        makeColumn("applied", makeItems("applied", 0, 60), 80, 60, true),
      ],
    };
    const getColumn = jest.fn().mockResolvedValue(
      makeColumn("applied", makeItems("applied", 20, 15), 35, 35, false),
    );

    const result = await refreshApplicationsBoardPreservingLoadedCounts(
      { currentBoard, search: "" },
      {
        getBoard: jest.fn().mockResolvedValue({
          columns: [
            makeColumn("applied", makeItems("applied", 0, 20), 35, 20, true),
          ],
        }),
        getColumn,
      },
    );

    expect(result.columns[0].items).toHaveLength(35);
    expect(getColumn).toHaveBeenCalledTimes(1);
  });

  it("terminates when duplicate pages stop advancing the offset", async () => {
    const initialItems = makeItems("applied", 0, 20);
    const currentBoard: ApplicationBoard = {
      columns: [makeColumn("applied", makeItems("applied", 0, 60), 80, 60, true)],
    };
    const getColumn = jest
      .fn()
      .mockResolvedValueOnce(
        makeColumn("applied", initialItems, 80, 40, true),
      )
      .mockResolvedValueOnce(
        makeColumn("applied", initialItems, 80, 40, true),
      );

    const result = await refreshApplicationsBoardPreservingLoadedCounts(
      { currentBoard, search: "" },
      {
        getBoard: jest.fn().mockResolvedValue({
          columns: [makeColumn("applied", initialItems, 80, 20, true)],
        }),
        getColumn,
      },
    );

    expect(result.columns[0].items).toHaveLength(20);
    expect(result.columns[0].nextOffset).toBe(40);
    expect(getColumn).toHaveBeenCalledTimes(2);
  });
});
