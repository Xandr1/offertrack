"use client";

import type { KeyboardEvent } from "react";
import type { ApplicationCreateMethod } from "../state/application-modal-reducer";

type ApplicationCreateMethodSwitchProps = {
  disabled?: boolean;
  method: ApplicationCreateMethod;
  onChange: (method: ApplicationCreateMethod) => void;
};

const methods: Array<{ label: string; value: ApplicationCreateMethod }> = [
  { label: "AI", value: "ai" },
  { label: "Manual", value: "manual" },
];

export const ApplicationCreateMethodSwitch = ({
  disabled = false,
  method,
  onChange,
}: ApplicationCreateMethodSwitchProps) => {
  const handleKeyDown = (
    event: KeyboardEvent<HTMLButtonElement>,
    currentMethod: ApplicationCreateMethod,
  ) => {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) {
      return;
    }

    event.preventDefault();
    const nextMethod =
      event.key === "ArrowLeft" || event.key === "Home"
        ? "ai"
        : event.key === "ArrowRight" || event.key === "End"
          ? "manual"
          : currentMethod;

    onChange(nextMethod);
    event.currentTarget.parentElement
      ?.querySelector<HTMLButtonElement>(`[data-create-method="${nextMethod}"]`)
      ?.focus();
  };

  return (
    <div
      aria-label="Creation mode"
      className="inline-flex rounded-lg bg-zinc-100 p-1"
      role="radiogroup"
    >
      {methods.map((option) => {
        const isSelected = method === option.value;

        return (
          <button
            aria-checked={isSelected}
            className={`min-h-9 rounded-md px-3 text-sm font-medium outline-none transition focus-visible:ring-2 focus-visible:ring-violet-500 focus-visible:ring-offset-1 ${
              isSelected
                ? "bg-white text-violet-700 shadow-sm"
                : "text-zinc-600 hover:text-zinc-950"
            } ${
              disabled
                ? "cursor-not-allowed opacity-60"
                : "cursor-pointer"
            }`}
            data-create-method={option.value}
            disabled={disabled}
            key={option.value}
            role="radio"
            tabIndex={isSelected ? 0 : -1}
            type="button"
            onClick={() => onChange(option.value)}
            onKeyDown={(event) => handleKeyDown(event, option.value)}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
};
