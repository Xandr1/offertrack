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
};

export const ApplicationModalFooter = ({
  formMode,
  isSaveDisabled,
  isSubmitting,
  modalError,
  onCancel,
}: ApplicationModalFooterProps) => {
  const isCreateMode = formMode === "create";

  return (
    <>
      {modalError && <div className={formStyles.error}>{modalError}</div>}

      <div className={modalStyles.softFooterBleed}>
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
    </>
  );
};
