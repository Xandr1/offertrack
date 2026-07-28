"use client";

import { FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/input";
import type { ApplicationSortField, SortDirection } from "@/lib/api";
import { sectionStyles } from "@/lib/styles";
import { StageFilter } from "../helpers/application-filters";
import type { ApplicationsView } from "../helpers/application-filters";
import {
  applicationSortFieldOptions,
  sortDirectionOptions,
  stageFilterOptions,
} from "../helpers/constants";
import { IconClose, IconSearch } from "./ui-icons";
import { ApplicationsViewToggle } from "./applications-view-toggle";

type ApplicationToolbarProps = {
  direction: SortDirection;
  searchInput: string;
  sort: ApplicationSortField;
  stageFilter: StageFilter;
  view: ApplicationsView;
  hasActiveFilters: boolean;
  onClearFilters: () => void;
  onSearchInputChange: (value: string) => void;
  onSearchClear: () => void;
  onSearchSubmit: () => void;
  onDirectionChange: (value: SortDirection) => void;
  onSortChange: (value: ApplicationSortField) => void;
  onStageChange: (value: StageFilter) => void;
  onViewChange: (value: ApplicationsView) => void;
};

export const ApplicationToolbar = ({
  direction,
  hasActiveFilters,
  searchInput,
  sort,
  stageFilter,
  view,
  onClearFilters,
  onDirectionChange,
  onSearchClear,
  onSearchInputChange,
  onSearchSubmit,
  onSortChange,
  onStageChange,
  onViewChange,
}: ApplicationToolbarProps) => {
  const handleSearchSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onSearchSubmit();
  };

  return (
    <section
      className={`${sectionStyles.toolbar} flex min-w-0 flex-col xl:grid xl:grid-cols-[auto_170px_minmax(280px,1fr)_180px_130px]`}
    >
      <div className="grid min-w-0 gap-3 sm:grid-cols-[auto_minmax(170px,1fr)] xl:contents">
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <ApplicationsViewToggle view={view} onChange={onViewChange} />
          {hasActiveFilters && (
            <Button
              className="whitespace-nowrap text-xs"
              onClick={onClearFilters}
              variant="ghost"
            >
              Clear filters
            </Button>
          )}
        </div>

        <div>
          <label className="sr-only">Stage</label>
          <Select
            aria-label="Stage"
            value={stageFilter}
            onChange={(event) =>
              onStageChange(event.target.value as StageFilter)
            }
          >
            {stageFilterOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </div>
      </div>

      <div className="grid min-w-0 gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(260px,1fr)_180px_130px] xl:contents">
        <form
          className="relative min-w-0 sm:col-span-2 lg:col-span-1"
          onSubmit={handleSearchSubmit}
        >
          <label className="sr-only">Search</label>
          <button
            aria-label="Search applications"
            className={sectionStyles.searchIconButton}
            type="submit"
          >
            <IconSearch className="h-4 w-4" />
          </button>
          <Input
            aria-label="Search applications"
            className="min-w-0 pr-10"
            placeholder="Search company or position..."
            variant="softWithIcon"
            value={searchInput}
            onChange={(event) => onSearchInputChange(event.target.value)}
          />
          {searchInput.trim() !== "" && (
            <button
              aria-label="Clear search"
              className={sectionStyles.clearSearchButton}
              type="button"
              onClick={onSearchClear}
            >
              <IconClose className="h-4 w-4" />
            </button>
          )}
        </form>

        <div>
          <label className="sr-only">Sort</label>
          <Select
            aria-label="Sort"
            value={sort}
            onChange={(event) =>
              onSortChange(event.target.value as ApplicationSortField)
            }
          >
            {applicationSortFieldOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </div>

        <div>
          <label className="sr-only">Direction</label>
          <Select
            aria-label="Direction"
            value={direction}
            onChange={(event) =>
              onDirectionChange(event.target.value as SortDirection)
            }
          >
            {sortDirectionOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </div>
      </div>
    </section>
  );
};
