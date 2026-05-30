"use client";

import { buttonStyles, formStyles, modalStyles } from "@/lib/styles";
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
        <button
          className={buttonStyles.secondarySoft}
          disabled={isSubmitting}
          onClick={onCancel}
          type="button"
        >
          Cancel
        </button>
        <button
          className={formStyles.inlinePrimarySoftButton}
          disabled={isSaveDisabled}
          type="submit"
        >
          {isSubmitting
            ? isCreateMode
              ? "Creating..."
              : "Saving..."
            : isCreateMode
              ? "Create application"
              : "Save changes"}
        </button>
      </div>
    </>
  );
};
