"use client";

import { Button } from "@/components/ui/button";
import { formStyles, modalStyles } from "@/lib/styles";
import { ApplicationFormMode } from "../models/application-form-model";

export type ApplicationModalFooterProps = {
  formMode: ApplicationFormMode;
  isSaveDisabled: boolean;
  isSubmitting: boolean;
  modalError: string | null;
  onCancel: () => void;
  onDelete?: () => void;
};

export const ApplicationModalFooter = ({
  formMode,
  isSaveDisabled,
  isSubmitting,
  modalError,
  onCancel,
  onDelete,
}: ApplicationModalFooterProps) => {
  const isCreateMode = formMode === "create";

  return (
    <>
      {modalError && (
        <div className={formStyles.error} role="alert">
          {modalError}
        </div>
      )}

      <div
        className={`${modalStyles.softFooterBleed} ${
          onDelete ? "justify-between" : "justify-end"
        }`}
      >
        {onDelete && (
          <Button
            disabled={isSubmitting}
            onClick={onDelete}
            variant="ghostDanger"
          >
            Delete
          </Button>
        )}
        <div className="flex items-center gap-3">
          <Button
            disabled={isSubmitting}
            onClick={onCancel}
            variant="secondarySoft"
          >
            Cancel
          </Button>
          <Button
            disabled={isSaveDisabled}
            type="submit"
            variant="primarySoft"
          >
            {isSubmitting
              ? isCreateMode
                ? "Creating..."
                : "Saving..."
              : isCreateMode
                ? "Create application"
                : "Save changes"}
          </Button>
        </div>
      </div>
    </>
  );
};
