"use client";

import { FormEvent } from "react";
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
  searchInput,
  sort,
  stageFilter,
  view,
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
      className={`${sectionStyles.toolbar} md:grid-cols-[auto_170px_minmax(0,1fr)_190px_130px]`}
    >
      <ApplicationsViewToggle view={view} onChange={onViewChange} />

      <div>
          <label className="sr-only">Stage</label>
          <Select
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
        className={
          view === "board"
            ? "relative w-full md:max-w-md"
            : "relative w-full"
        }
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
          className="pr-10"
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
    </section>
  );
};
