import type { Application, ApplicationBoard } from "@/lib/api";
import {
  appendBoardColumn,
  moveBoardApplication,
  replaceBoardApplication,
} from "./application-board-cache";

const application = (id: string, stage: Application["stage"]): Application => ({
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

const board = (): ApplicationBoard => ({
  columns: [
    {
      stage: "applied",
      totalCount: 2,
      items: [application("app-1", "applied")],
      nextOffset: 1,
      hasMore: true,
    },
    {
      stage: "interviewing",
      totalCount: 0,
      items: [],
      nextOffset: 0,
      hasMore: false,
    },
  ],
});

describe("application board cache", () => {
  it("appends and deduplicates one column page", () => {
    const result = appendBoardColumn(board(), {
      stage: "applied",
      totalCount: 2,
      items: [application("app-1", "applied"), application("app-2", "applied")],
      nextOffset: 2,
      hasMore: false,
    });

    expect(result.columns[0].items.map((item) => item.id)).toEqual([
      "app-1",
      "app-2",
    ]);
    expect(result.columns[0].nextOffset).toBe(2);
    expect(result.columns[0].hasMore).toBe(false);
  });

  it("moves a card and updates both column counts and offsets", () => {
    const result = moveBoardApplication(board(), "app-1", "interviewing");

    expect(result.columns[0]).toMatchObject({
      totalCount: 1,
      nextOffset: 0,
      hasMore: true,
    });
    expect(result.columns[1]).toMatchObject({
      totalCount: 1,
      nextOffset: 1,
      hasMore: false,
    });
    expect(result.columns[1].items[0]).toMatchObject({
      id: "app-1",
      stage: "interviewing",
    });
  });

  it("replaces the optimistic card with the server result", () => {
    const moved = moveBoardApplication(board(), "app-1", "interviewing");
    const result = replaceBoardApplication(moved, {
      ...application("app-1", "interviewing"),
      updatedAt: "2026-06-28T12:00:00Z",
    });

    expect(result.columns[1].items[0].updatedAt).toBe(
      "2026-06-28T12:00:00Z",
    );
  });
});
