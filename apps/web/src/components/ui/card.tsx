import { ComponentPropsWithoutRef, ElementType } from "react";
import { cardStyles } from "@/lib/styles";

const cardVariantClassNames = {
  auth: cardStyles.auth,
  default: cardStyles.default,
  soft: cardStyles.soft,
  dashed: cardStyles.dashed,
  application: cardStyles.application,
  board: cardStyles.board,
};

type CardVariant = keyof typeof cardVariantClassNames;

type CardProps<T extends ElementType> = {
  as?: T;
  className?: string;
  variant?: CardVariant;
} & Omit<ComponentPropsWithoutRef<T>, "as" | "className">;

export const Card = <T extends ElementType = "div">({
  as,
  className,
  variant = "default",
  ...props
}: CardProps<T>) => {
  const Component = (as ?? "div") as ElementType;

  return (
    <Component
      {...props}
      className={[cardVariantClassNames[variant], className].filter(Boolean).join(" ")}
    />
  );
};
