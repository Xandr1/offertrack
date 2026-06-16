"use client";

import { Button } from "@/components/ui/button";
import { sectionStyles, textStyles } from "@/lib/styles";

type ApplicationsPaginationProps = {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
};

export const ApplicationsPagination = ({
  page,
  totalPages,
  onPageChange,
}: ApplicationsPaginationProps) => {
  const hasPages = totalPages > 0;
  const canGoPrevious = hasPages && page > 0;
  const canGoNext = hasPages && page + 1 < totalPages;

  return (
    <div
      className={`${sectionStyles.topBorderRow} mt-4 justify-between border-zinc-200 pt-4`}
    >
      <Button
        disabled={!canGoPrevious}
        onClick={() => onPageChange(page - 1)}
        variant="secondarySoft"
        type="button"
      >
        Previous
      </Button>
      <p className={textStyles.muted}>
        Page {hasPages ? page + 1 : 0} of {totalPages}
      </p>
      <Button
        disabled={!canGoNext}
        onClick={() => onPageChange(page + 1)}
        variant="secondarySoft"
        type="button"
      >
        Next
      </Button>
    </div>
  );
};
