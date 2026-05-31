import { ButtonHTMLAttributes } from "react";
import { buttonStyles, formStyles } from "@/lib/styles";

const buttonVariantClassNames = {
  primary: formStyles.primaryButton,
  primarySoft: formStyles.inlinePrimarySoftButton,
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

export const Button = ({
  className,
  type,
  variant = "secondary",
  ...props
}: ButtonProps) => (
  <button
    {...props}
    className={[buttonVariantClassNames[variant], className].filter(Boolean).join(" ")}
    type={type ?? "button"}
  />
);
