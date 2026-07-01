const MILLISECONDS_PER_CALENDAR_DAY = 24 * 60 * 60 * 1000;

export type WaitingTone = "yellow" | "amber" | "orange" | "red";
export type UpcomingTone = "future" | "tomorrow" | "today";

const getLocalCalendarOrdinal = (date: Date): number =>
  Date.UTC(date.getFullYear(), date.getMonth(), date.getDate());

export const getWholeCalendarDaysBetween = (from: string, now: Date): number => {
  const fromDate = new Date(from);
  return Math.round(
    (getLocalCalendarOrdinal(now) - getLocalCalendarOrdinal(fromDate)) /
    MILLISECONDS_PER_CALENDAR_DAY,
  );
};

export const formatWaitingLabel = (days: number): string => `Waiting ${days} days`;

export const formatWaitingResultLabel = (days: number): string =>
  `Waiting result ${days} days`;

export const getWaitingUrgencyTone = (
  days: number,
  minDays: number,
  maxDays: number,
): WaitingTone => {
  const spread = maxDays - minDays;
  if (spread < 3) return "yellow";

  const urgency = Math.min(1, Math.max(0, (days - minDays) / spread));
  if (urgency <= 0.25) return "yellow";
  if (urgency <= 0.5) return "amber";
  if (urgency <= 0.75) return "orange";
  return "red";
};

export const getUpcomingInterviewTiming = (
  scheduledAt: string,
  now: Date,
): { label: string; tone: UpcomingTone } => {
  const daysUntil = -getWholeCalendarDaysBetween(scheduledAt, now);

  if (daysUntil === 0) {
    const time = new Intl.DateTimeFormat("en", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }).format(new Date(scheduledAt));
    return { label: `Today at ${time}`, tone: "today" };
  }

  if (daysUntil === 1) return { label: "Tomorrow", tone: "tomorrow" };
  return { label: `In ${daysUntil} days`, tone: "future" };
};

export const joinMetadata = (
  parts: Array<string | null | undefined>,
): string | null => {
  const metadata = parts
    .map((part) => part?.trim())
    .filter((part): part is string => Boolean(part));
  return metadata.length > 0 ? metadata.join(" · ") : null;
};
