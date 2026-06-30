"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type {
  ApplicationSortField,
  ApplicationStage,
  ApplicationsListParams,
  SortDirection,
} from "@/lib/api";
import {
  APPLICATIONS_VIEW_DEFAULT,
  STAGE_FILTER_DEFAULT,
  ApplicationsView,
  canonicalizeApplicationsViewParams,
  getStoredApplicationsView,
  storeApplicationsView,
  StageFilter,
  parseDirectionParam,
  parsePageParam,
  parseSearchQueryParam,
  parseSizeParam,
  parseSortParam,
  parseStageFilterParam,
  toListParams,
  writeApplicationsBoardParams,
  writeApplicationsListParams,
} from "../helpers/application-filters";

type SetFiltersInput = {
  direction?: SortDirection;
  search?: string;
  sort?: ApplicationSortField;
  stage?: StageFilter;
};

const toUrl = (pathname: string, params: URLSearchParams): string => {
  const query = params.toString();
  return query ? `${pathname}?${query}` : pathname;
};

const isNonNegativeIntegerParam = (value: string | null): boolean => {
  if (value === null || value.trim() === "") {
    return false;
  }

  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0;
};

export const useApplicationsUrlFilters = () => {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();

  const rawPageParam = searchParams.get("page");
  const hasInvalidPageParam =
    searchParams.has("page") && !isNonNegativeIntegerParam(rawPageParam);
  const page = parsePageParam(rawPageParam);
  const size = parseSizeParam(searchParams.get("size"));
  const stageFilter = parseStageFilterParam(searchParams.get("stage"));
  const sort = parseSortParam(searchParams.get("sort"));
  const direction = parseDirectionParam(searchParams.get("direction"));
  const searchQuery = parseSearchQueryParam(searchParams.get("search"));
  const selectedApplicationId = searchParams.get("id")?.trim() || null;
  const [view, setViewState] = useState<ApplicationsView>(APPLICATIONS_VIEW_DEFAULT);
  const [isViewInitialized, setIsViewInitialized] = useState(false);
  const [searchInput, setSearchInput] = useState(searchQuery);

  const listParams: ApplicationsListParams = useMemo(
    () =>
      toListParams({
        direction,
        page,
        searchQuery,
        size,
        sort,
        stageFilter,
      }),
    [direction, page, searchQuery, size, sort, stageFilter],
  );

  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setViewState(getStoredApplicationsView());
      setIsViewInitialized(true);
    }, 0);

    return () => {
      clearTimeout(timeoutId);
    };
  }, []);

  useEffect(() => {
    if (!isViewInitialized) {
      return;
    }

    const currentParams = new URLSearchParams(searchParams.toString());
    const canonicalParams = canonicalizeApplicationsViewParams(
      currentParams,
      view,
    );
    const currentUrl = toUrl(pathname, currentParams);
    const canonicalUrl = toUrl(pathname, canonicalParams);

    if (canonicalUrl !== currentUrl) {
      router.replace(canonicalUrl, { scroll: false });
    }
  }, [isViewInitialized, pathname, router, searchParams, view]);

  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setSearchInput((currentValue) =>
        currentValue === searchQuery ? currentValue : searchQuery,
      );
    }, 0);

    return () => {
      clearTimeout(timeoutId);
    };
  }, [searchQuery]);

  const replaceListParams = useCallback(
    (nextListParams: ApplicationsListParams) => {
      const params = canonicalizeApplicationsViewParams(
        new URLSearchParams(searchParams.toString()),
        "list",
      );
      writeApplicationsListParams(params, nextListParams);
      router.replace(toUrl(pathname, params), { scroll: false });
    },
    [pathname, router, searchParams],
  );

  const setFilters = useCallback(
    (nextValues: SetFiltersInput) => {
      const nextDirection = nextValues.direction ?? direction;
      const nextSearch = (nextValues.search ?? searchQuery).trim();
      const nextSort = nextValues.sort ?? sort;

      if (view === "board") {
        const params = canonicalizeApplicationsViewParams(
          new URLSearchParams(searchParams.toString()),
          "board",
        );
        writeApplicationsBoardParams(params, {
          direction: nextDirection,
          search: nextSearch,
          sort: nextSort,
        });
        router.replace(toUrl(pathname, params), { scroll: false });
        return;
      }

      const nextStage = nextValues.stage ?? stageFilter;
      const nextStageParam =
        nextStage === STAGE_FILTER_DEFAULT ? null : (nextStage as ApplicationStage);

      replaceListParams({
        direction: nextDirection,
        page: 0,
        search: nextSearch,
        size,
        sort: nextSort,
        stage: nextStageParam,
      });
    },
    [
      direction,
      pathname,
      replaceListParams,
      router,
      searchQuery,
      searchParams,
      size,
      sort,
      stageFilter,
      view,
    ],
  );

  const setPage = useCallback(
    (nextPage: number) => {
      replaceListParams({
        ...listParams,
        page: Math.max(0, nextPage),
      });
    },
    [listParams, replaceListParams],
  );

  const clearPageParam = useCallback(() => {
    const params = new URLSearchParams(searchParams.toString());
    params.delete("page");
    router.replace(toUrl(pathname, params), { scroll: false });
  }, [pathname, router, searchParams]);

  const setSelectedApplicationId = useCallback(
    (applicationId: string) => {
      const params = new URLSearchParams(searchParams.toString());
      params.set("id", applicationId);
      router.replace(toUrl(pathname, params), { scroll: false });
    },
    [pathname, router, searchParams],
  );

  const clearSelectedApplicationId = useCallback(() => {
    const params = new URLSearchParams(searchParams.toString());
    params.delete("id");
    router.replace(toUrl(pathname, params), { scroll: false });
  }, [pathname, router, searchParams]);

  const setView = useCallback(
    (nextView: ApplicationsView) => {
      setViewState(nextView);
      storeApplicationsView(nextView);

      const params = canonicalizeApplicationsViewParams(
        new URLSearchParams(searchParams.toString()),
        nextView,
      );
      router.replace(toUrl(pathname, params), { scroll: false });
    },
    [pathname, router, searchParams],
  );

  return {
    clearPageParam,
    clearSelectedApplicationId,
    direction,
    hasInvalidPageParam,
    isViewInitialized,
    listParams,
    page,
    searchInput,
    searchQuery,
    selectedApplicationId,
    setFilters,
    setPage,
    setSearchInput,
    setSelectedApplicationId,
    setView,
    size,
    sort,
    stageFilter,
    view,
  };
};
