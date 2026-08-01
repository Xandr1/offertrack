import type { ReactNode } from "react";

type EmptyStateProps = {
  action?: ReactNode;
  className?: string;
  compact?: boolean;
  description: string;
  title: string;
};

export const EmptyState = ({
  action,
  className,
  compact = false,
  description,
  title,
}: EmptyStateProps) => (
  <div
    className={[
      "border border-dashed border-zinc-300 bg-zinc-50/60 text-center",
      compact ? "rounded-lg px-3 py-4" : "rounded-2xl px-5 py-7",
      className,
    ]
      .filter(Boolean)
      .join(" ")}
  >
    <p className="text-sm font-medium text-zinc-800">{title}</p>
    <p className="mt-1 text-sm text-zinc-500">{description}</p>
    {action && <div className="mt-3 flex justify-center">{action}</div>}
  </div>
);
