"use client";

import { Input, Select } from "@/components/ui/input";
import { sectionStyles } from "@/lib/styles";
import {
  ApplicationsSort,
  StageFilter,
} from "../helpers/application-filters";
import { sortOptions, stageFilterOptions } from "../helpers/constants";
import { IconSearch } from "./ui-icons";

type ApplicationToolbarProps = {
  searchInput: string;
  sort: ApplicationsSort;
  stageFilter: StageFilter;
  onSearchInputChange: (value: string) => void;
  onSortChange: (value: ApplicationsSort) => void;
  onStageChange: (value: StageFilter) => void;
};

export const ApplicationToolbar = ({
  searchInput,
  sort,
  stageFilter,
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
          onChange={(event) => onSortChange(event.target.value as ApplicationsSort)}
        >
          {sortOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
      </div>
    </section>
  );
};
