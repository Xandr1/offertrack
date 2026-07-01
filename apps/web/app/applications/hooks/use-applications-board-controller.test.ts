/** @jest-environment jsdom */

import React from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  getApplicationBoardColumn,
  getApplicationsBoard,
  updateApplicationStage,
} from "@/lib/api";
import type { Application, ApplicationBoard } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { useApplicationsBoardController } from "./use-applications-board-controller";

jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"),
  getApplicationBoardColumn: jest.fn(),
  getApplicationsBoard: jest.fn(),
  updateApplicationStage: jest.fn(),
}));

const mockedGetColumn = getApplicationBoardColumn as jest.MockedFunction<
  typeof getApplicationBoardColumn
>;
const mockedGetBoard = getApplicationsBoard as jest.MockedFunction<
  typeof getApplicationsBoard
>;
const mockedUpdateStage = updateApplicationStage as jest.MockedFunction<
  typeof updateApplicationStage
>;

const makeApplication = (
  id: string,
  stage: Application["stage"],
): Application => ({
  appliedAt: null,
  companyName: `Company ${id}`,
  createdAt: "2026-01-01T00:00:00Z",
  id,
  jobUrl: null,
  location: null,
  followedUpAt: null,
  lastInterview: null,
  nextInterview: null,
  notes: null,
  positionTitle: "Engineer",
  stage,
  updatedAt: "2026-01-01T00:00:00Z",
  workMode: null,
});

const makeBoard = (): ApplicationBoard => ({
  columns: [
    {
      stage: "applied",
      totalCount: 50,
      items: [makeApplication("app-1", "applied")],
      nextOffset: 20,
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

const defaultBoardParams = {
  search: "",
  stage: null,
  sort: "updatedAt",
  direction: "desc",
} as const;

const renderController = () => {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  queryClient.setQueryData(
    queryKeys.applications.board(defaultBoardParams),
    makeBoard(),
  );
  const onMutationError = jest.fn();
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
  const hook = renderHook(
    () =>
      useApplicationsBoardController({
        enabled: false,
        ...defaultBoardParams,
        onMutationError,
      }),
    { wrapper },
  );

  return { ...hook, onMutationError, queryClient };
};

describe("useApplicationsBoardController", () => {
  beforeEach(() => {
    mockedGetBoard.mockReset();
    mockedGetColumn.mockReset();
    mockedUpdateStage.mockReset();
  });

  it("loads the next page using backend nextOffset", async () => {
    mockedGetColumn.mockResolvedValue({
      stage: "applied",
      totalCount: 50,
      items: [makeApplication("app-2", "applied")],
      nextOffset: 40,
      hasMore: true,
    });
    const { result, queryClient } = renderController();

    act(() => result.current.loadMore("applied"));

    expect(result.current.loadMoreState.applied?.isLoading).toBe(true);
    await waitFor(() => expect(mockedGetColumn).toHaveBeenCalledWith({
      columnStage: "applied",
      ...defaultBoardParams,
      offset: 20,
    }));
    await waitFor(() =>
      expect(
        queryClient
          .getQueryData<ApplicationBoard>(
            queryKeys.applications.board(defaultBoardParams),
          )
          ?.columns[0].items,
      ).toHaveLength(2),
    );
    expect(result.current.loadMoreState.applied).toEqual({
      isLoading: false,
      error: null,
    });
  });

  it("uses sort and direction in the query key and refetches when they change", async () => {
    mockedGetBoard.mockResolvedValue(makeBoard());
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const wrapper = ({ children }: { children: React.ReactNode }) =>
      React.createElement(QueryClientProvider, { client: queryClient }, children);
    const { rerender } = renderHook(
      ({ sort, direction }: { sort: "updatedAt" | "createdAt"; direction: "asc" | "desc" }) =>
        useApplicationsBoardController({
          enabled: true,
          search: "acme",
          stage: null,
          sort,
          direction,
          onMutationError: jest.fn(),
        }),
      {
        initialProps: {
          sort: "updatedAt" as "updatedAt" | "createdAt",
          direction: "desc" as "asc" | "desc",
        },
        wrapper,
      },
    );

    await waitFor(() =>
      expect(mockedGetBoard).toHaveBeenCalledWith({
        search: "acme",
        stage: null,
        sort: "updatedAt",
        direction: "desc",
      }),
    );

    rerender({ sort: "createdAt", direction: "asc" });

    await waitFor(() =>
      expect(mockedGetBoard).toHaveBeenCalledWith({
        search: "acme",
        stage: null,
        sort: "createdAt",
        direction: "asc",
      }),
    );
    expect(
      queryClient.getQueryState(
        queryKeys.applications.board({
          search: "acme",
          stage: null,
          sort: "updatedAt",
          direction: "desc",
        }),
      ),
    ).toBeDefined();
    expect(
      queryClient.getQueryState(
        queryKeys.applications.board({
          search: "acme",
          stage: null,
          sort: "createdAt",
          direction: "asc",
        }),
      ),
    ).toBeDefined();
  });

  it("blocks load more while a stage update is pending", async () => {
    let resolveStageUpdate: (application: Application) => void = () => undefined;
    mockedUpdateStage.mockReturnValue(
      new Promise<Application>((resolve) => {
        resolveStageUpdate = resolve;
      }),
    );
    const { result } = renderController();

    act(() =>
      result.current.handleDragEnd({
        active: { id: "app-1" },
        over: { id: "interviewing" },
      } as never),
    );
    await waitFor(() => expect(result.current.isStageUpdatePending).toBe(true));

    act(() => result.current.loadMore("applied"));
    expect(mockedGetColumn).not.toHaveBeenCalled();

    resolveStageUpdate(makeApplication("app-1", "interviewing"));
    await waitFor(() => expect(result.current.isStageUpdatePending).toBe(false));
  });

  it("optimistically moves across stages and ignores same-stage drops", async () => {
    mockedUpdateStage.mockResolvedValue(
      makeApplication("app-1", "interviewing"),
    );
    const { result, queryClient } = renderController();

    act(() =>
      result.current.handleDragEnd({
        active: { id: "app-1" },
        over: { id: "interviewing" },
      } as never),
    );

    await waitFor(() =>
      expect(
        queryClient
          .getQueryData<ApplicationBoard>(
            queryKeys.applications.board(defaultBoardParams),
          )
          ?.columns[1].items[0]?.stage,
      ).toBe("interviewing"),
    );
    await waitFor(() =>
      expect(mockedUpdateStage).toHaveBeenCalledWith("app-1", "interviewing"),
    );

    act(() =>
      result.current.handleDragEnd({
        active: { id: "app-1" },
        over: { id: "interviewing" },
      } as never),
    );
    expect(mockedUpdateStage).toHaveBeenCalledTimes(1);
  });

  it("rolls back a failed stage update", async () => {
    mockedUpdateStage.mockRejectedValue(new Error("update failed"));
    const { result, queryClient, onMutationError } = renderController();

    act(() =>
      result.current.handleDragEnd({
        active: { id: "app-1" },
        over: { id: "interviewing" },
      } as never),
    );

    await waitFor(() => expect(onMutationError).toHaveBeenCalled());
    const current = queryClient.getQueryData<ApplicationBoard>(
      queryKeys.applications.board(defaultBoardParams),
    );
    expect(current?.columns[0].items[0].stage).toBe("applied");
    expect(current?.columns[1].items).toHaveLength(0);
  });

  it("keeps the stale cache and reports a board refresh failure", async () => {
    mockedGetBoard.mockRejectedValue(new Error("refresh failed"));
    const { result, queryClient, onMutationError } = renderController();
    const previousBoard = queryClient.getQueryData<ApplicationBoard>(
      queryKeys.applications.board(defaultBoardParams),
    );

    await act(async () => {
      await result.current.refreshPreservingLoadedCounts();
    });

    expect(onMutationError).toHaveBeenCalledWith(expect.any(Error));
    expect(mockedGetBoard).toHaveBeenCalledWith(defaultBoardParams);
    expect(
      queryClient.getQueryData<ApplicationBoard>(
        queryKeys.applications.board(defaultBoardParams),
      ),
    ).toEqual(previousBoard);
  });
});
