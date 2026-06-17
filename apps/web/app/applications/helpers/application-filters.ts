import {
  APPLICATIONS_LIST_DEFAULTS,
} from "@/lib/api";
import type {
  ApplicationSortField,
  ApplicationStage,
  ApplicationsListParams,
  SortDirection,
} from "@/lib/api";
import {
  applicationSortFieldOptions,
  sortDirectionOptions,
  stageFilterOptions,
} from "./constants";

export type StageFilter = "all" | ApplicationStage;

export const STAGE_FILTER_DEFAULT: StageFilter = "all";

const validStageFilters = new Set<StageFilter>(
  stageFilterOptions.map((option) => option.value),
);
const validSortFields = new Set<ApplicationSortField>(
  applicationSortFieldOptions.map((option) => option.value),
);
const validDirections = new Set<SortDirection>(
  sortDirectionOptions.map((option) => option.value),
);

export const parseStageFilterParam = (value: string | null): StageFilter => {
  if (!value) {
    return STAGE_FILTER_DEFAULT;
  }

  return validStageFilters.has(value as StageFilter)
    ? (value as StageFilter)
    : STAGE_FILTER_DEFAULT;
};

export const parseSortParam = (value: string | null): ApplicationSortField => {
  if (!value) {
    return APPLICATIONS_LIST_DEFAULTS.sort;
  }

  return validSortFields.has(value as ApplicationSortField)
    ? (value as ApplicationSortField)
    : APPLICATIONS_LIST_DEFAULTS.sort;
};

export const parseDirectionParam = (value: string | null): SortDirection => {
  if (!value) {
    return APPLICATIONS_LIST_DEFAULTS.direction;
  }

  return validDirections.has(value as SortDirection)
    ? (value as SortDirection)
    : APPLICATIONS_LIST_DEFAULTS.direction;
};

export const parsePageParam = (value: string | null): number => {
  if (!value) {
    return APPLICATIONS_LIST_DEFAULTS.page;
  }

  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0
    ? parsed
    : APPLICATIONS_LIST_DEFAULTS.page;
};

export const parseSizeParam = (value: string | null): number => {
  if (!value) {
    return APPLICATIONS_LIST_DEFAULTS.size;
  }

  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 1 && parsed <= 100
    ? parsed
    : APPLICATIONS_LIST_DEFAULTS.size;
};

export const parseSearchQueryParam = (value: string | null): string => {
  if (!value) {
    return "";
  }

  return value.trim();
};

export const toListParams = ({
  direction,
  page,
  searchQuery,
  size,
  sort,
  stageFilter,
}: {
  direction: SortDirection;
  page: number;
  searchQuery: string;
  size: number;
  sort: ApplicationSortField;
  stageFilter: StageFilter;
}): ApplicationsListParams => ({
  direction,
  page,
  search: searchQuery,
  size,
  sort,
  stage: stageFilter === STAGE_FILTER_DEFAULT ? null : stageFilter,
});

export const writeApplicationsListParams = (
  params: URLSearchParams,
  listParams: ApplicationsListParams,
) => {
  if (listParams.page === APPLICATIONS_LIST_DEFAULTS.page) {
    params.delete("page");
  } else {
    params.set("page", String(listParams.page));
  }

  if (listParams.size === APPLICATIONS_LIST_DEFAULTS.size) {
    params.delete("size");
  } else {
    params.set("size", String(listParams.size));
  }

  const trimmedSearch = listParams.search.trim();
  if (trimmedSearch === "") {
    params.delete("search");
  } else {
    params.set("search", trimmedSearch);
  }

  if (listParams.stage === null) {
    params.delete("stage");
  } else {
    params.set("stage", listParams.stage);
  }

  if (listParams.sort === APPLICATIONS_LIST_DEFAULTS.sort) {
    params.delete("sort");
  } else {
    params.set("sort", listParams.sort);
  }

  if (listParams.direction === APPLICATIONS_LIST_DEFAULTS.direction) {
    params.delete("direction");
  } else {
    params.set("direction", listParams.direction);
  }
};
