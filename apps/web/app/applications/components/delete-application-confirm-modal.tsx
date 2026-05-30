"use client";

import { buttonStyles, modalStyles } from "@/lib/styles";
import { ApplicationModal } from "./application-modal";

type DeleteApplicationConfirmModalProps = {
  isDeleting: boolean;
  isOpen: boolean;
  onCancel: () => void;
  onConfirm: () => void;
};

export const DeleteApplicationConfirmModal = ({
  isDeleting,
  isOpen,
  onCancel,
  onConfirm,
}: DeleteApplicationConfirmModalProps) => {
  return (
    <ApplicationModal
      description="This will remove the application and all its interview rounds."
      isCompact
      isCloseDisabled={isDeleting}
      isOpen={isOpen}
      title="Delete application?"
      onClose={onCancel}
    >
      <div className={modalStyles.softFooterBleedCompact}>
        <button
          className={buttonStyles.secondarySoft}
          disabled={isDeleting}
          onClick={onCancel}
          type="button"
        >
          Cancel
        </button>
        <button
          className={buttonStyles.danger}
          disabled={isDeleting}
          onClick={onConfirm}
          type="button"
        >
          {isDeleting ? "Deleting..." : "Delete application"}
        </button>
      </div>
    </ApplicationModal>
  );
};
