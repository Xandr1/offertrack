"use client";

import { ReactNode, useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";
import { Button } from "@/components/ui/button";
import { modalStyles, textStyles } from "@/lib/styles";
import { IconClose } from "./ui-icons";

type ApplicationModalVariant = "default" | "compact" | "ai";

type ApplicationModalProps = {
  children: ReactNode;
  description?: string;
  headerControls?: ReactNode;
  isOpen: boolean;
  isCloseDisabled?: boolean;
  initialFocusSelector?: string;
  title: string;
  variant?: ApplicationModalVariant;
  onClose: () => void;
};

const focusableElementSelector =
  'a[href], button, input:not([type="hidden"]), select, textarea, [tabindex]';

const isElementVisible = (
  element: HTMLElement,
  panel: HTMLElement,
): boolean => {
  if (
    !element.isConnected ||
    element.closest('[hidden], [inert], [aria-hidden="true"]') ||
    element.getAttribute("aria-disabled") === "true"
  ) {
    return false;
  }

  let currentElement: HTMLElement | null = element;
  while (currentElement) {
    const style = window.getComputedStyle(currentElement);
    if (
      style.display === "none" ||
      style.visibility === "hidden" ||
      style.visibility === "collapse" ||
      style.opacity === "0" ||
      style.contentVisibility === "hidden"
    ) {
      return false;
    }

    currentElement = currentElement.parentElement;
  }

  const browserProvidesLayout =
    document.documentElement.getClientRects().length > 0;
  return (
    !browserProvidesLayout ||
    (panel.getClientRects().length > 0 &&
      element.getClientRects().length > 0)
  );
};

const getFocusableElements = (panel: HTMLElement): HTMLElement[] =>
  Array.from(
    panel.querySelectorAll<HTMLElement>(focusableElementSelector),
  ).filter(
    (element) =>
      element.tabIndex >= 0 &&
      !element.matches(":disabled") &&
      isElementVisible(element, panel),
  );

export const ApplicationModal = ({
  children,
  description,
  headerControls,
  isOpen,
  isCloseDisabled = false,
  initialFocusSelector,
  title,
  variant = "default",
  onClose,
}: ApplicationModalProps) => {
  const titleId = useId();
  const descriptionId = useId();
  const overlayRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const openerRef = useRef<HTMLElement | null>(null);
  const closeDisabledRef = useRef(isCloseDisabled);
  const onCloseRef = useRef(onClose);

  useEffect(() => {
    closeDisabledRef.current = isCloseDisabled;
  }, [isCloseDisabled]);

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    openerRef.current =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const modalRoot = overlayRef.current;
    panelRef.current?.focus();
    const backgroundElements = Array.from(document.body.children)
      .filter((element) => element !== modalRoot)
      .map((element) => ({
        element,
        hadInert: element.hasAttribute("inert"),
        previousAriaHidden: element.getAttribute("aria-hidden"),
      }));

    backgroundElements.forEach(({ element }) => {
      element.setAttribute("inert", "");
      element.setAttribute("aria-hidden", "true");
    });

    const handleKeyDown = (event: KeyboardEvent) => {
      const panel = panelRef.current;
      if (!panel) {
        return;
      }

      if (event.key === "Escape") {
        if (!closeDisabledRef.current) {
          event.preventDefault();
          onCloseRef.current();
        }
        return;
      }

      if (event.key !== "Tab") {
        return;
      }

      const focusableElements = getFocusableElements(panel);

      if (focusableElements.length === 0) {
        event.preventDefault();
        panel.focus();
        return;
      }

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];
      const activeElement = document.activeElement;

      if (event.shiftKey && activeElement === firstElement) {
        event.preventDefault();
        lastElement.focus();
      } else if (!event.shiftKey && activeElement === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      backgroundElements.forEach(
        ({ element, hadInert, previousAriaHidden }) => {
          if (!hadInert) {
            element.removeAttribute("inert");
          }

          if (previousAriaHidden === null) {
            element.removeAttribute("aria-hidden");
          } else {
            element.setAttribute("aria-hidden", previousAriaHidden);
          }
        },
      );

      if (openerRef.current?.isConnected) {
        openerRef.current.focus();
      }
    };
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    queueMicrotask(() => {
      const panel = panelRef.current;
      if (!panel) {
        return;
      }

      const initialElement = initialFocusSelector
        ? panel.querySelector<HTMLElement>(initialFocusSelector)
        : null;
      const focusableElements = getFocusableElements(panel);
      const validInitialElement =
        initialElement &&
        initialElement.tabIndex >= 0 &&
        !initialElement.matches(":disabled") &&
        isElementVisible(initialElement, panel)
          ? initialElement
          : null;
      const fallbackElement = focusableElements[0] ?? null;

      (validInitialElement ?? fallbackElement ?? panel).focus();
    });
  }, [initialFocusSelector, isOpen]);

  if (!isOpen || typeof document === "undefined") {
    return null;
  }

  const modalContent = (
    <div
      className={modalStyles.softContainer}
      ref={overlayRef}
      onClick={() => {
        if (!isCloseDisabled) {
          onClose();
        }
      }}
    >
      <div
        aria-describedby={description ? descriptionId : undefined}
        aria-labelledby={titleId}
        aria-modal="true"
        className={
          variant === "compact"
            ? modalStyles.compactPanel
            : variant === "ai"
              ? modalStyles.aiPanel
              : modalStyles.softPanel
        }
        ref={panelRef}
        role="dialog"
        tabIndex={-1}
        onClick={(event) => {
          event.stopPropagation();
        }}
      >
        <div
          className={
            variant === "default"
              ? modalStyles.softHeader
              : modalStyles.compactHeader
          }
        >
          <div>
            <h2 className={textStyles.sectionTitle} id={titleId}>
              {title}
            </h2>
            {description && (
              <p className={textStyles.subtitle} id={descriptionId}>
                {description}
              </p>
            )}
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

        <div
          className={
            variant === "default"
              ? modalStyles.softBody
              : modalStyles.compactBody
          }
        >
          {children}
        </div>
      </div>
    </div>
  );

  return createPortal(modalContent, document.body);
};
