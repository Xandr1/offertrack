import {
  APPLICATIONS_LIST_DEFAULTS,
} from "@/lib/api";
import type {
  ApplicationSortField,
  ApplicationStage,
  ApplicationsBoardParams,
  ApplicationsListParams,
  SortDirection,
} from "@/lib/api";
import {
  applicationSortFieldOptions,
  sortDirectionOptions,
  stageFilterOptions,
} from "./constants";

export type StageFilter = "all" | ApplicationStage;
export type ApplicationsView = "list" | "board";

export const STAGE_FILTER_DEFAULT: StageFilter = "all";
export const APPLICATIONS_VIEW_DEFAULT: ApplicationsView = "list";

export const parseStoredApplicationsView = (
  value: string | null,
): ApplicationsView => (value === "board" ? "board" : APPLICATIONS_VIEW_DEFAULT);

export const getStoredApplicationsView = (): ApplicationsView => {
  if (typeof window === "undefined") {
    return APPLICATIONS_VIEW_DEFAULT;
  }

  try {
    return parseStoredApplicationsView(
      window.localStorage.getItem("offertrack.applications.view"),
    );
  } catch {
    return APPLICATIONS_VIEW_DEFAULT;
  }
};

export const storeApplicationsView = (view: ApplicationsView) => {
  try {
    window.localStorage.setItem("offertrack.applications.view", view);
  } catch {
    // The in-memory selection still works when storage is unavailable.
  }
};

const viewSortFields = new Set<ApplicationSortField>([
  "updatedAt",
  "createdAt",
]);

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

export const writeApplicationsBoardParams = (
  params: URLSearchParams,
  boardParams: ApplicationsBoardParams,
) => {
  const trimmedSearch = boardParams.search.trim();
  if (trimmedSearch === "") {
    params.delete("search");
  } else {
    params.set("search", trimmedSearch);
  }

  if (boardParams.sort === APPLICATIONS_LIST_DEFAULTS.sort) {
    params.delete("sort");
  } else {
    params.set("sort", boardParams.sort);
  }

  if (boardParams.direction === APPLICATIONS_LIST_DEFAULTS.direction) {
    params.delete("direction");
  } else {
    params.set("direction", boardParams.direction);
  }
};

const copyTrimmedParam = (
  source: URLSearchParams,
  target: URLSearchParams,
  name: string,
) => {
  const value = source.get(name)?.trim();
  if (value) {
    target.set(name, value);
  }
};

const copyListOnlyParams = (
  source: URLSearchParams,
  target: URLSearchParams,
) => {
  const stage = source.get("stage")?.trim();
  if (
    stage &&
    stage !== STAGE_FILTER_DEFAULT &&
    validStageFilters.has(stage as StageFilter)
  ) {
    target.set("stage", stage);
  }

  const rawPage = source.get("page")?.trim();
  const parsedPage = rawPage ? Number(rawPage) : Number.NaN;
  if (
    Number.isInteger(parsedPage) &&
    parsedPage >= 0 &&
    parsedPage !== APPLICATIONS_LIST_DEFAULTS.page
  ) {
    target.set("page", String(parsedPage));
  }

  const rawSize = source.get("size")?.trim();
  const parsedSize = rawSize ? Number(rawSize) : Number.NaN;
  if (
    Number.isInteger(parsedSize) &&
    parsedSize >= 1 &&
    parsedSize <= 100 &&
    parsedSize !== APPLICATIONS_LIST_DEFAULTS.size
  ) {
    target.set("size", String(parsedSize));
  }
};

const copyCanonicalSortParams = (
  source: URLSearchParams,
  target: URLSearchParams,
) => {
  const rawSort = source.get("sort")?.trim() ?? "";
  const sortIsDefault =
    rawSort === "" || rawSort === APPLICATIONS_LIST_DEFAULTS.sort;
  const sortIsValid =
    sortIsDefault || viewSortFields.has(rawSort as ApplicationSortField);

  if (!sortIsValid) {
    return;
  }

  if (!sortIsDefault) {
    target.set("sort", rawSort);
  }

  const rawDirection = source.get("direction")?.trim() ?? "";
  if (
    validDirections.has(rawDirection as SortDirection) &&
    rawDirection !== APPLICATIONS_LIST_DEFAULTS.direction
  ) {
    target.set("direction", rawDirection);
  }
};

export const canonicalizeApplicationsViewParams = (
  source: URLSearchParams,
  view: ApplicationsView,
): URLSearchParams => {
  const result = new URLSearchParams();

  copyTrimmedParam(source, result, "search");
  copyTrimmedParam(source, result, "id");

  if (view === "list") {
    copyListOnlyParams(source, result);
  }

  copyCanonicalSortParams(source, result);
  return result;
};
