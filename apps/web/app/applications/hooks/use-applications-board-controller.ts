"use client";

import { useCallback, useEffect, useMemo, useReducer, useRef } from "react";
import type { DragEndEvent } from "@dnd-kit/core";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getApplicationBoardColumn,
  getApplicationsBoard,
  updateApplicationStage,
} from "@/lib/api";
import type {
  ApplicationBoard,
  ApplicationBoardColumn,
  ApplicationStage,
  ApplicationSortField,
  ApplicationsBoardParams,
  SortDirection,
} from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { getRequestErrorMessage } from "@/lib/request-errors";
import { applicationStages } from "../helpers/constants";
import { invalidateAfterBoardStageChange } from "../services/applications-cache-service";
import { refreshApplicationsBoardPreservingLoadedCounts } from "../services/applications-board-refresh";
import {
  appendBoardColumn,
  moveBoardApplication,
  removeBoardApplication,
  replaceBoardApplication,
} from "../state/application-board-cache";

type StageLoadState = {
  isLoading: boolean;
  error: string | null;
};

export type BoardLoadMoreState = Partial<
  Record<ApplicationStage, StageLoadState>
>;

type LoadStateAction =
  | { type: "reset" }
  | { type: "start"; stage: ApplicationStage }
  | { type: "success"; stage: ApplicationStage }
  | { type: "error"; stage: ApplicationStage; error: string };

const loadStateReducer = (
  state: BoardLoadMoreState,
  action: LoadStateAction,
): BoardLoadMoreState => {
  if (action.type === "reset") {
    return {};
  }

  if (action.type === "start") {
    return { ...state, [action.stage]: { isLoading: true, error: null } };
  }

  if (action.type === "success") {
    return { ...state, [action.stage]: { isLoading: false, error: null } };
  }

  return {
    ...state,
    [action.stage]: { isLoading: false, error: action.error },
  };
};

type LoadMoreVariables = ApplicationsBoardParams & {
  columnStage: ApplicationStage;
  offset: number;
};

type StageMutationVariables = {
  applicationId: string;
  targetStage: ApplicationStage;
};

type StageMutationContext = {
  previousBoard?: ApplicationBoard;
};

type UseApplicationsBoardControllerParams = {
  enabled: boolean;
  search: string;
  stage: ApplicationStage | null;
  sort: ApplicationSortField;
  direction: SortDirection;
  onMutationError: (error: unknown) => void | Promise<void>;
};

const validStages = new Set<ApplicationStage>(applicationStages);
const toLoadRequestKey = (variables: LoadMoreVariables): string =>
  JSON.stringify([
    variables.search,
    variables.sort,
    variables.direction,
    variables.stage,
    variables.columnStage,
  ]);
const toBoardScopeKey = (params: ApplicationsBoardParams): string =>
  JSON.stringify([params.search, params.stage, params.sort, params.direction]);

export const useApplicationsBoardController = ({
  enabled,
  search,
  stage,
  sort,
  direction,
  onMutationError,
}: UseApplicationsBoardControllerParams) => {
  const queryClient = useQueryClient();
  const normalizedSearch = search.trim();
  const boardParams = useMemo(
    () => ({ search: normalizedSearch, stage, sort, direction }),
    [direction, normalizedSearch, sort, stage],
  );
  const boardQueryKey = queryKeys.applications.board(boardParams);
  const boardScopeKey = toBoardScopeKey(boardParams);
  const [loadMoreState, dispatchLoadState] = useReducer(loadStateReducer, {});
  const loadingRequestKeysRef = useRef(new Set<string>());
  const activeBoardScopeRef = useRef(boardScopeKey);

  const boardQuery = useQuery({
    enabled,
    queryFn: () => getApplicationsBoard(boardParams),
    queryKey: boardQueryKey,
    retry: false,
  });

  useEffect(() => {
    activeBoardScopeRef.current = boardScopeKey;
    loadingRequestKeysRef.current.clear();
    dispatchLoadState({ type: "reset" });
  }, [boardScopeKey]);

  const loadMoreMutation = useMutation<
    ApplicationBoardColumn,
    unknown,
    LoadMoreVariables
  >({
    mutationFn: (variables) => getApplicationBoardColumn(variables),
    onMutate: (variables) => {
      loadingRequestKeysRef.current.add(toLoadRequestKey(variables));
      dispatchLoadState({ type: "start", stage: variables.columnStage });
    },
    onSuccess: (page, variables) => {
      queryClient.setQueryData<ApplicationBoard>(
        queryKeys.applications.board({
          search: variables.search,
          stage: variables.stage,
          sort: variables.sort,
          direction: variables.direction,
        }),
        (current) => (current ? appendBoardColumn(current, page) : current),
      );

      if (toBoardScopeKey(variables) === activeBoardScopeRef.current) {
        dispatchLoadState({ type: "success", stage: variables.columnStage });
      }
    },
    onError: (error, variables) => {
      if (toBoardScopeKey(variables) === activeBoardScopeRef.current) {
        dispatchLoadState({
          type: "error",
          stage: variables.columnStage,
          error: getRequestErrorMessage(error),
        });
        void onMutationError(error);
      }
    },
    onSettled: (_data, _error, variables) => {
      loadingRequestKeysRef.current.delete(toLoadRequestKey(variables));
    },
  });

  const updateStageMutation = useMutation<
    Awaited<ReturnType<typeof updateApplicationStage>>,
    unknown,
    StageMutationVariables,
    StageMutationContext
  >({
    mutationFn: ({ applicationId, targetStage }) =>
      updateApplicationStage(applicationId, targetStage),
    onMutate: async ({ applicationId, targetStage }) => {
      await queryClient.cancelQueries({ queryKey: boardQueryKey, exact: true });
      const previousBoard =
        queryClient.getQueryData<ApplicationBoard>(boardQueryKey);

      if (previousBoard) {
        queryClient.setQueryData<ApplicationBoard>(
          boardQueryKey,
          moveBoardApplication(previousBoard, applicationId, targetStage, stage),
        );
      }

      return { previousBoard };
    },
    onSuccess: (application) => {
      queryClient.setQueryData<ApplicationBoard>(boardQueryKey, (current) =>
        current ? replaceBoardApplication(current, application) : current,
      );
      invalidateAfterBoardStageChange(queryClient, application.id);
    },
    onError: (error, _variables, context) => {
      if (context?.previousBoard) {
        queryClient.setQueryData(boardQueryKey, context.previousBoard);
      }
      void onMutationError(error);
    },
  });

  const loadMore = useCallback(
    (stage: ApplicationStage) => {
      if (
        updateStageMutation.isPending ||
        loadingRequestKeysRef.current.has(
          toLoadRequestKey({ ...boardParams, columnStage: stage, offset: 0 }),
        )
      ) {
        return;
      }

      const board = queryClient.getQueryData<ApplicationBoard>(boardQueryKey);
      const column = board?.columns.find((item) => item.stage === stage);
      if (!column?.hasMore) {
        return;
      }

      loadMoreMutation.mutate({
        columnStage: stage,
        ...boardParams,
        offset: column.nextOffset,
      });
    },
    [
      boardQueryKey,
      loadMoreMutation,
      boardParams,
      queryClient,
      updateStageMutation.isPending,
    ],
  );

  const refreshPreservingLoadedCounts = useCallback(async () => {
    const currentBoard =
      queryClient.getQueryData<ApplicationBoard>(boardQueryKey);

    try {
      const refreshedBoard =
        await refreshApplicationsBoardPreservingLoadedCounts({
          currentBoard,
          ...boardParams,
        });
      queryClient.setQueryData(boardQueryKey, refreshedBoard);
      return true;
    } catch (error) {
      await onMutationError(error);
      return false;
    }
  }, [boardParams, boardQueryKey, onMutationError, queryClient]);

  const removeApplication = useCallback(
    (applicationId: string) => {
      queryClient.setQueryData<ApplicationBoard>(boardQueryKey, (current) =>
        current
          ? removeBoardApplication(current, applicationId)
          : current,
      );
    },
    [boardQueryKey, queryClient],
  );

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const applicationId = String(event.active.id);
      const targetStage = event.over?.id;
      if (
        updateStageMutation.isPending ||
        typeof targetStage !== "string" ||
        !validStages.has(targetStage as ApplicationStage)
      ) {
        return;
      }

      const board = queryClient.getQueryData<ApplicationBoard>(boardQueryKey);
      const sourceColumn = board?.columns.find((column) =>
        column.items.some((item) => item.id === applicationId),
      );
      if (!sourceColumn || sourceColumn.stage === targetStage) {
        return;
      }

      updateStageMutation.mutate({
        applicationId,
        targetStage: targetStage as ApplicationStage,
      });
    },
    [boardQueryKey, queryClient, updateStageMutation],
  );

  return {
    boardQuery,
    handleDragEnd,
    isStageUpdatePending: updateStageMutation.isPending,
    loadMore,
    loadMoreState,
    removeApplication,
    refreshPreservingLoadedCounts,
  };
};
