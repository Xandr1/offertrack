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
  STAGE_FILTER_DEFAULT,
  StageFilter,
  parseDirectionParam,
  parsePageParam,
  parseSearchQueryParam,
  parseSizeParam,
  parseSortParam,
  parseStageFilterParam,
  toListParams,
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

export const useApplicationsUrlFilters = () => {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();

  const page = parsePageParam(searchParams.get("page"));
  const size = parseSizeParam(searchParams.get("size"));
  const stageFilter = parseStageFilterParam(searchParams.get("stage"));
  const sort = parseSortParam(searchParams.get("sort"));
  const direction = parseDirectionParam(searchParams.get("direction"));
  const searchQuery = parseSearchQueryParam(searchParams.get("search"));
  const selectedApplicationId = searchParams.get("id")?.trim() || null;
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
      const params = new URLSearchParams(searchParams.toString());
      writeApplicationsListParams(params, nextListParams);
      router.replace(toUrl(pathname, params), { scroll: false });
    },
    [pathname, router, searchParams],
  );

  const setFilters = useCallback(
    (nextValues: SetFiltersInput) => {
      const nextStage = nextValues.stage ?? stageFilter;
      const nextStageParam =
        nextStage === STAGE_FILTER_DEFAULT ? null : (nextStage as ApplicationStage);

      replaceListParams({
        direction: nextValues.direction ?? direction,
        page: 0,
        search: (nextValues.search ?? searchQuery).trim(),
        size,
        sort: nextValues.sort ?? sort,
        stage: nextStageParam,
      });
    },
    [
      direction,
      replaceListParams,
      searchQuery,
      size,
      sort,
      stageFilter,
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

  useEffect(() => {
    const timeoutId = setTimeout(() => {
      if (searchInput === searchQuery) {
        return;
      }

      setFilters({ search: searchInput });
    }, 300);

    return () => {
      clearTimeout(timeoutId);
    };
  }, [searchInput, searchQuery, setFilters]);

  return {
    clearSelectedApplicationId,
    direction,
    listParams,
    page,
    searchInput,
    searchQuery,
    selectedApplicationId,
    setFilters,
    setPage,
    setSearchInput,
    setSelectedApplicationId,
    size,
    sort,
    stageFilter,
  };
};
