"use client";

import { useCallback, useEffect, useReducer, useRef } from "react";
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
} from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { getRequestErrorMessage } from "@/lib/request-errors";
import { applicationStages } from "../helpers/constants";
import { refreshApplicationsBoardPreservingLoadedCounts } from "../services/applications-board-refresh";
import {
  appendBoardColumn,
  moveBoardApplication,
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

type LoadMoreVariables = {
  stage: ApplicationStage;
  search: string;
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
  onMutationError: (error: unknown) => void | Promise<void>;
};

const validStages = new Set<ApplicationStage>(applicationStages);

export const useApplicationsBoardController = ({
  enabled,
  search,
  onMutationError,
}: UseApplicationsBoardControllerParams) => {
  const queryClient = useQueryClient();
  const normalizedSearch = search.trim();
  const boardQueryKey = queryKeys.applications.board(normalizedSearch);
  const [loadMoreState, dispatchLoadState] = useReducer(loadStateReducer, {});
  const loadingRequestKeysRef = useRef(new Set<string>());
  const activeSearchRef = useRef(normalizedSearch);

  const boardQuery = useQuery({
    enabled,
    queryFn: () => getApplicationsBoard(normalizedSearch),
    queryKey: boardQueryKey,
    retry: false,
  });

  useEffect(() => {
    activeSearchRef.current = normalizedSearch;
    loadingRequestKeysRef.current.clear();
    dispatchLoadState({ type: "reset" });
  }, [normalizedSearch]);

  const loadMoreMutation = useMutation<
    ApplicationBoardColumn,
    unknown,
    LoadMoreVariables
  >({
    mutationFn: (variables) => getApplicationBoardColumn(variables),
    onMutate: (variables) => {
      loadingRequestKeysRef.current.add(`${variables.search}:${variables.stage}`);
      dispatchLoadState({ type: "start", stage: variables.stage });
    },
    onSuccess: (page, variables) => {
      queryClient.setQueryData<ApplicationBoard>(
        queryKeys.applications.board(variables.search),
        (current) => (current ? appendBoardColumn(current, page) : current),
      );

      if (variables.search === activeSearchRef.current) {
        dispatchLoadState({ type: "success", stage: variables.stage });
      }
    },
    onError: (error, variables) => {
      if (variables.search === activeSearchRef.current) {
        dispatchLoadState({
          type: "error",
          stage: variables.stage,
          error: getRequestErrorMessage(error),
        });
        void onMutationError(error);
      }
    },
    onSettled: (_data, _error, variables) => {
      loadingRequestKeysRef.current.delete(`${variables.search}:${variables.stage}`);
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
          moveBoardApplication(previousBoard, applicationId, targetStage),
        );
      }

      return { previousBoard };
    },
    onSuccess: (application) => {
      queryClient.setQueryData<ApplicationBoard>(boardQueryKey, (current) =>
        current ? replaceBoardApplication(current, application) : current,
      );
      void queryClient.invalidateQueries({
        queryKey: queryKeys.applications.board(),
        refetchType: "none",
      });
      void queryClient.invalidateQueries({
        queryKey: queryKeys.applications.list(),
      });
      void queryClient.invalidateQueries({ queryKey: queryKeys.dashboardSummary });
      void queryClient.invalidateQueries({
        queryKey: queryKeys.applications.detail(application.id),
      });
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
        loadingRequestKeysRef.current.has(`${normalizedSearch}:${stage}`)
      ) {
        return;
      }

      const board = queryClient.getQueryData<ApplicationBoard>(boardQueryKey);
      const column = board?.columns.find((item) => item.stage === stage);
      if (!column?.hasMore) {
        return;
      }

      loadMoreMutation.mutate({
        stage,
        search: normalizedSearch,
        offset: column.nextOffset,
      });
    },
    [
      boardQueryKey,
      loadMoreMutation,
      normalizedSearch,
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
          search: normalizedSearch,
        });
      queryClient.setQueryData(boardQueryKey, refreshedBoard);
    } catch (error) {
      await onMutationError(error);
    }
  }, [boardQueryKey, normalizedSearch, onMutationError, queryClient]);

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
    refreshPreservingLoadedCounts,
  };
};
