"use client";

import { formStyles, sectionStyles } from "@/lib/styles";
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
        <select
          className={formStyles.selectSoft}
          value={stageFilter}
          onChange={(event) => onStageChange(event.target.value as StageFilter)}
        >
          {stageFilterOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      <div className="relative">
        <label className="sr-only">Search</label>
        <IconSearch className={sectionStyles.searchIcon} />
        <input
          className={formStyles.inputSoftWithIcon}
          placeholder="Search company or position..."
          value={searchInput}
          onChange={(event) => onSearchInputChange(event.target.value)}
        />
      </div>

      <div>
        <label className="sr-only">Sort</label>
        <select
          className={formStyles.selectSoft}
          value={sort}
          onChange={(event) => onSortChange(event.target.value as ApplicationsSort)}
        >
          {sortOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
    </section>
  );
};
