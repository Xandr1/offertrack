import { ButtonHTMLAttributes } from "react";
import { buttonStyles, formStyles } from "@/lib/styles";

const buttonVariantClassNames = {
  primary: formStyles.primaryButton,
  primarySoft: buttonStyles.primarySoft,
  secondary: buttonStyles.secondary,
  danger: buttonStyles.danger,
  secondarySoft: buttonStyles.secondarySoft,
  secondarySoftAccent: buttonStyles.secondarySoftAccent,
  ghost: buttonStyles.ghost,
  ghostDanger: buttonStyles.ghostDanger,
  iconGhost: buttonStyles.iconGhost,
  iconGhostDanger: buttonStyles.iconGhostDanger,
  textAccent: buttonStyles.textAccent,
};

export type ButtonVariant = keyof typeof buttonVariantClassNames;

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
};

const baseButtonClassName =
  "cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-violet-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed";

export const Button = ({
  className,
  type,
  variant = "secondary",
  ...props
}: ButtonProps) => (
  <button
    {...props}
    className={[baseButtonClassName, buttonVariantClassNames[variant], className]
      .filter(Boolean)
      .join(" ")}
    type={type ?? "button"}
  />
);
