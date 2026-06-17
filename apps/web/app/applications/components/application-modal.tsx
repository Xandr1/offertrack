"use client";

import { ReactNode, useEffect } from "react";
import { createPortal } from "react-dom";
import { Button } from "@/components/ui/button";
import { modalStyles, textStyles } from "@/lib/styles";
import { IconClose } from "./ui-icons";

type ApplicationModalProps = {
  children: ReactNode;
  description?: string;
  headerControls?: ReactNode;
  isCompact?: boolean;
  isOpen: boolean;
  isCloseDisabled?: boolean;
  title: string;
  onClose: () => void;
};

export const ApplicationModal = ({
  children,
  description,
  headerControls,
  isCompact = false,
  isOpen,
  isCloseDisabled = false,
  title,
  onClose,
}: ApplicationModalProps) => {
  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [isOpen]);

  if (!isOpen || typeof document === "undefined") {
    return null;
  }

  const modalContent = (
    <div
      className={modalStyles.softContainer}
      onClick={() => {
        if (!isCloseDisabled) {
          onClose();
        }
      }}
    >
      <div
        className={isCompact ? modalStyles.compactPanel : modalStyles.softPanel}
        onClick={(event) => {
          event.stopPropagation();
        }}
      >
        <div className={isCompact ? modalStyles.compactHeader : modalStyles.softHeader}>
          <div>
            <h2 className={textStyles.sectionTitle}>{title}</h2>
            {description && <p className={textStyles.subtitle}>{description}</p>}
          </div>

          <div className="flex shrink-0 items-center gap-4">
            {headerControls}
            <Button
              aria-label="Close modal"
              disabled={isCloseDisabled}
              onClick={onClose}
              variant="iconGhost"
              type="button"
            >
              <IconClose className="h-[18px] w-[18px]" />
            </Button>
          </div>
        </div>

        <div className={isCompact ? modalStyles.compactBody : modalStyles.softBody}>
          {children}
        </div>
      </div>
    </div>
  );

  return createPortal(modalContent, document.body);
};
