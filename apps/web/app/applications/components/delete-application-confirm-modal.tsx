"use client";

import { Button } from "@/components/ui/button";
import { modalStyles } from "@/lib/styles";
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
      isCloseDisabled={isDeleting}
      isOpen={isOpen}
      title="Delete application?"
      variant="compact"
      onClose={onCancel}
    >
      <div className={modalStyles.softFooterBleedCompact}>
        <Button
          disabled={isDeleting}
          onClick={onCancel}
          variant="secondarySoft"
        >
          Cancel
        </Button>
        <Button
          disabled={isDeleting}
          onClick={onConfirm}
          variant="danger"
        >
          {isDeleting ? "Deleting..." : "Delete application"}
        </Button>
      </div>
    </ApplicationModal>
  );
};
