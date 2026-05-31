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

export const Input = ({ className, variant = "soft", ...props }: InputProps) => (
  <input
    {...props}
    className={[inputVariantClassNames[variant], className].filter(Boolean).join(" ")}
  />
);

export const Select = ({ className, variant = "soft", ...props }: SelectProps) => (
  <select
    {...props}
    className={[selectVariantClassNames[variant], className].filter(Boolean).join(" ")}
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
