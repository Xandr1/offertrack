"use client";

import { Input, Select } from "@/components/ui/input";
import type { ApplicationSortField, SortDirection } from "@/lib/api";
import { sectionStyles } from "@/lib/styles";
import { StageFilter } from "../helpers/application-filters";
import {
  applicationSortFieldOptions,
  sortDirectionOptions,
  stageFilterOptions,
} from "../helpers/constants";
import { IconSearch } from "./ui-icons";

type ApplicationToolbarProps = {
  direction: SortDirection;
  searchInput: string;
  sort: ApplicationSortField;
  stageFilter: StageFilter;
  onSearchInputChange: (value: string) => void;
  onDirectionChange: (value: SortDirection) => void;
  onSortChange: (value: ApplicationSortField) => void;
  onStageChange: (value: StageFilter) => void;
};

export const ApplicationToolbar = ({
  direction,
  searchInput,
  sort,
  stageFilter,
  onDirectionChange,
  onSearchInputChange,
  onSortChange,
  onStageChange,
}: ApplicationToolbarProps) => {
  return (
    <section className={sectionStyles.toolbar}>
      <div>
        <label className="sr-only">Stage</label>
        <Select
          value={stageFilter}
          onChange={(event) => onStageChange(event.target.value as StageFilter)}
        >
          {stageFilterOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
      </div>

      <div className="relative">
        <label className="sr-only">Search</label>
        <IconSearch className={sectionStyles.searchIcon} />
        <Input
          placeholder="Search company or position..."
          variant="softWithIcon"
          value={searchInput}
          onChange={(event) => onSearchInputChange(event.target.value)}
        />
      </div>

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
