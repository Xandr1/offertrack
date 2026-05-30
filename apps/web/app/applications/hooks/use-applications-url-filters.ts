"use client";

import { useCallback, useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  ApplicationsSort,
  STAGE_FILTER_DEFAULT,
  SORT_DEFAULT,
  StageFilter,
  parseSearchQueryParam,
  parseSortParam,
  parseStageFilterParam,
} from "../helpers/application-filters";

type SetFiltersInput = {
  q?: string;
  sort?: ApplicationsSort;
  stage?: StageFilter;
};

export const useApplicationsUrlFilters = () => {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();

  const stageFilter = parseStageFilterParam(searchParams.get("stage"));
  const sort = parseSortParam(searchParams.get("sort"));
  const searchQuery = parseSearchQueryParam(searchParams.get("q"));
  const [searchInput, setSearchInput] = useState(searchQuery);

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

  const setFilters = useCallback(
    (nextValues: SetFiltersInput) => {
      const params = new URLSearchParams(searchParams.toString());

      const nextStage = nextValues.stage ?? stageFilter;
      const nextSort = nextValues.sort ?? sort;
      const nextQuery = (nextValues.q ?? searchQuery).trim();

      if (nextStage === STAGE_FILTER_DEFAULT) {
        params.delete("stage");
      } else {
        params.set("stage", nextStage);
      }

      if (nextSort === SORT_DEFAULT) {
        params.delete("sort");
      } else {
        params.set("sort", nextSort);
      }

      if (nextQuery === "") {
        params.delete("q");
      } else {
        params.set("q", nextQuery);
      }

      const nextUrl = params.toString() ? `${pathname}?${params.toString()}` : pathname;
      router.replace(nextUrl, { scroll: false });
    },
    [pathname, router, searchParams, searchQuery, sort, stageFilter],
  );

  useEffect(() => {
    const timeoutId = setTimeout(() => {
      if (searchInput === searchQuery) {
        return;
      }

      setFilters({ q: searchInput });
    }, 300);

    return () => {
      clearTimeout(timeoutId);
    };
  }, [searchInput, searchQuery, setFilters]);

  return {
    searchInput,
    searchQuery,
    setFilters,
    setSearchInput,
    sort,
    stageFilter,
  };
};
