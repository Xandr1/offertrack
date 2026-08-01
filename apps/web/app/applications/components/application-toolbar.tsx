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
      aria-label="Applications controls"
      className={`${sectionStyles.toolbar} flex min-w-0 flex-wrap justify-start`}
      data-testid="applications-toolbar"
    >
      <div className="order-1 shrink-0">
        <ApplicationsViewToggle view={view} onChange={onViewChange} />
      </div>

      <div className="order-2 w-full min-w-0 sm:w-[170px] sm:shrink-0">
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

      <form
        className="relative order-3 w-full min-w-0 shrink-0 lg:order-6 2xl:order-3 2xl:w-auto 2xl:min-w-[280px] 2xl:flex-1"
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

      <div
        className={
          "order-4 w-full min-w-0 sm:w-[180px] sm:shrink-0 " +
          "lg:order-3 2xl:order-4"
        }
      >
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

      <div
        className={
          "order-5 w-full min-w-0 sm:w-[130px] sm:shrink-0 " +
          "lg:order-4 2xl:order-5"
        }
      >
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

      {hasActiveFilters && (
        <Button
          className="order-6 shrink-0 whitespace-nowrap text-xs lg:order-5 2xl:order-6"
          onClick={onClearFilters}
          variant="ghost"
        >
          Clear filters
        </Button>
      )}
    </section>
  );
};
