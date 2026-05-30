import { formatDateTime } from "@/lib/date-format";

const pad = (value: number): string => String(value).padStart(2, "0");
const SECOND_IN_MS = 1_000;
const MINUTE_IN_SECONDS = 60;
const HOUR_IN_SECONDS = 60 * MINUTE_IN_SECONDS;
const DAY_IN_SECONDS = 24 * HOUR_IN_SECONDS;

const parseDate = (value: string): Date | null => {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
};

const parseNullableDate = (value: string | null): Date | null => {
  if (!value) {
    return null;
  }

  return parseDate(value);
};

const parseTrimmedDate = (value: string): Date | null => {
  const trimmedValue = value.trim();
  if (!trimmedValue) {
    return null;
  }

  return parseDate(trimmedValue);
};

const getRelativeTimeFormatter = (): Intl.RelativeTimeFormat | null => {
  if (
    typeof Intl === "undefined" ||
    typeof Intl.RelativeTimeFormat !== "function"
  ) {
    return null;
  }

  return new Intl.RelativeTimeFormat("en", {
    numeric: "auto",
  });
};

const isSameCalendarDate = (left: Date, right: Date): boolean => {
  return (
    left.getFullYear() === right.getFullYear() &&
    left.getMonth() === right.getMonth() &&
    left.getDate() === right.getDate()
  );
};

const toDateValue = (value: string | Date): Date => {
  return value instanceof Date ? value : new Date(value);
};

const toDateTimeString = (value: string | Date): string => {
  return value instanceof Date ? value.toISOString() : value;
};

export const formatUpdatedAtAbsolute = (value: string | Date): string => {
  return `Updated ${formatDateTime(toDateTimeString(value))}`;
};

export const toApiDateFromDateInput = (value: string): string | null => {
  const trimmedValue = value.trim();
  if (!trimmedValue) {
    return null;
  }

  const parsed = parseDate(`${trimmedValue}T00:00:00.000Z`);
  if (!parsed) {
    return null;
  }

  return parsed.toISOString();
};

export const toDateInputFromApi = (value: string | null): string => {
  const parsed = parseNullableDate(value);
  if (!parsed) {
    return "";
  }

  return parsed.toISOString().slice(0, 10);
};

export const dateTimeLocalToApiDateTime = (value: string): string | null => {
  const parsed = parseTrimmedDate(value);
  if (!parsed) {
    return null;
  }

  return parsed.toISOString();
};

export const apiDateTimeToDateTimeLocal = (value: string | null): string => {
  const parsed = parseNullableDate(value);
  if (!parsed) {
    return "";
  }

  return `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())}T${pad(parsed.getHours())}:${pad(parsed.getMinutes())}`;
};

export const formatDisplayDateTime = (value: string | null): string => {
  if (!value) {
    return "Not set";
  }

  return formatDateTime(value);
};

export const formatUpdatedAtRelative = (value: string | Date): string => {
  const fallback = (): string => formatUpdatedAtAbsolute(value);
  const parsed = toDateValue(value);

  if (Number.isNaN(parsed.getTime())) {
    return fallback();
  }

  const now = new Date();
  const diffInSeconds = Math.floor((now.getTime() - parsed.getTime()) / SECOND_IN_MS);

  if (diffInSeconds < 0) {
    return fallback();
  }

  if (diffInSeconds < MINUTE_IN_SECONDS) {
    return "Updated Recently";
  }

  try {
    const relativeTimeFormatter = getRelativeTimeFormatter();
    if (!relativeTimeFormatter) {
      return fallback();
    }

    if (diffInSeconds < HOUR_IN_SECONDS) {
      const minutes = Math.max(1, Math.floor(diffInSeconds / MINUTE_IN_SECONDS));
      return `Updated ${relativeTimeFormatter.format(-minutes, "minute")}`;
    }

    if (diffInSeconds < DAY_IN_SECONDS) {
      const hours = Math.max(1, Math.floor(diffInSeconds / HOUR_IN_SECONDS));
      return `Updated ${relativeTimeFormatter.format(-hours, "hour")}`;
    }

    const yesterday = new Date(now);
    yesterday.setDate(now.getDate() - 1);
    if (isSameCalendarDate(parsed, yesterday)) {
      return "Updated yesterday";
    }

    return fallback();
  } catch {
    return fallback();
  }
};
