import {
  InputHTMLAttributes,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from "react";
import { formStyles } from "@/lib/styles";

const inputVariantClassNames = {
  auth: formStyles.input,
  soft: formStyles.inputSoft,
  softWithIcon: formStyles.inputSoftWithIcon,
};

const selectVariantClassNames = {
  soft: formStyles.selectSoft,
  interviewStatus: formStyles.interviewStatusSelect,
};

const textareaVariantClassNames = {
  soft: formStyles.textareaSoft,
};

export type InputVariant = keyof typeof inputVariantClassNames;
export type SelectVariant = keyof typeof selectVariantClassNames;
export type TextareaVariant = keyof typeof textareaVariantClassNames;

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  variant?: InputVariant;
};

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  variant?: SelectVariant;
};

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  variant?: TextareaVariant;
};

const pointerInputTypes = new Set(["checkbox", "date", "datetime-local", "radio"]);

export const Input = ({
  className,
  type,
  variant = "soft",
  ...props
}: InputProps) => {
  const pointerClassName =
    type && pointerInputTypes.has(type)
      ? "cursor-pointer disabled:cursor-not-allowed"
      : undefined;

  return (
    <input
      {...props}
      className={[inputVariantClassNames[variant], pointerClassName, className]
        .filter(Boolean)
        .join(" ")}
      type={type}
    />
  );
};

export const Select = ({ className, variant = "soft", ...props }: SelectProps) => (
  <select
    {...props}
    className={[
      selectVariantClassNames[variant],
      "cursor-pointer disabled:cursor-not-allowed",
      className,
    ]
      .filter(Boolean)
      .join(" ")}
  />
);

export const Textarea = ({
  className,
  variant = "soft",
  ...props
}: TextareaProps) => (
  <textarea
    {...props}
    className={[textareaVariantClassNames[variant], className]
      .filter(Boolean)
      .join(" ")}
  />
);
