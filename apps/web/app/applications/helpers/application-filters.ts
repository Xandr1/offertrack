import { Application, ApplicationStage } from "@/lib/api";
import { sortOptions, stageFilterOptions } from "./constants";

export type StageFilter = "all" | ApplicationStage;
export type ApplicationsSort = (typeof sortOptions)[number]["value"];

export const STAGE_FILTER_DEFAULT: StageFilter = "all";
export const SORT_DEFAULT: ApplicationsSort = "updated_desc";

const validStageFilters = new Set<StageFilter>(
  stageFilterOptions.map((option) => option.value),
);
const validSorts = new Set<ApplicationsSort>(
  sortOptions.map((option) => option.value),
);

export const parseStageFilterParam = (value: string | null): StageFilter => {
  if (!value) {
    return STAGE_FILTER_DEFAULT;
  }

  return validStageFilters.has(value as StageFilter)
    ? (value as StageFilter)
    : STAGE_FILTER_DEFAULT;
};

export const parseSortParam = (value: string | null): ApplicationsSort => {
  if (!value) {
    return SORT_DEFAULT;
  }

  return validSorts.has(value as ApplicationsSort)
    ? (value as ApplicationsSort)
    : SORT_DEFAULT;
};

export const parseSearchQueryParam = (value: string | null): string => {
  if (!value) {
    return "";
  }

  return value.trim();
};

const matchesSearch = (application: Application, searchQuery: string): boolean => {
  if (!searchQuery) {
    return true;
  }

  const loweredQuery = searchQuery.toLowerCase();
  return (
    application.companyName.toLowerCase().includes(loweredQuery) ||
    application.positionTitle.toLowerCase().includes(loweredQuery)
  );
};

const compareBySort = (a: Application, b: Application, sort: ApplicationsSort): number => {
  const updatedA = new Date(a.updatedAt).getTime();
  const updatedB = new Date(b.updatedAt).getTime();
  const createdA = new Date(a.createdAt).getTime();
  const createdB = new Date(b.createdAt).getTime();

  switch (sort) {
    case "updated_asc":
      return updatedA - updatedB;
    case "created_desc":
      return createdB - createdA;
    case "created_asc":
      return createdA - createdB;
    case "updated_desc":
    default:
      return updatedB - updatedA;
  }
};

export const filterAndSortApplications = ({
  applications,
  searchQuery,
  sort,
  stageFilter,
}: {
  applications: Application[];
  stageFilter: StageFilter;
  searchQuery: string;
  sort: ApplicationsSort;
}): Application[] => {
  return applications
    .filter((application) => {
      if (stageFilter !== "all" && application.stage !== stageFilter) {
        return false;
      }

      return matchesSearch(application, searchQuery);
    })
    .sort((a, b) => compareBySort(a, b, sort));
};
