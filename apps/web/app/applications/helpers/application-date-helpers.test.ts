import {
  apiDateTimeToDateTimeLocal,
  dateTimeLocalToApiDateTime,
  formatDisplayDateTime,
  formatUpdatedAtRelative,
  toApiDateFromDateInput,
  toDateInputFromApi,
} from "./application-date-helpers";
import { formatDateTime } from "@/lib/date-format";

describe("application-date-helpers", () => {
  it("maps datetime-local input to API datetime", () => {
    expect(dateTimeLocalToApiDateTime("")).toBeNull();
    expect(dateTimeLocalToApiDateTime("  ")).toBeNull();

    const localValue = "2026-01-09T08:45";
    expect(dateTimeLocalToApiDateTime(localValue)).toBe(
      new Date(localValue).toISOString(),
    );
  });

  it("maps API datetime to datetime-local input", () => {
    expect(apiDateTimeToDateTimeLocal(null)).toBe("");
    expect(apiDateTimeToDateTimeLocal("2026-01-09T08:45:00.000Z")).toMatch(
      /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/,
    );
  });

  it("maps date input to API date and back", () => {
    expect(toApiDateFromDateInput("")).toBeNull();
    expect(toApiDateFromDateInput("2026-03-12")).toBe("2026-03-12T00:00:00.000Z");
    expect(toDateInputFromApi("2026-03-12T00:00:00.000Z")).toBe("2026-03-12");
    expect(toDateInputFromApi(null)).toBe("");
  });

  it("formats display datetime with null fallback", () => {
    expect(formatDisplayDateTime(null)).toBe("Not set");
    expect(formatDisplayDateTime("2026-03-12T08:30:00.000Z")).toEqual(
      expect.any(String),
    );
  });

  describe("formatUpdatedAtRelative", () => {
    const now = new Date("2026-05-29T12:00:00.000Z");

    beforeEach(() => {
      jest.useFakeTimers();
      jest.setSystemTime(now);
    });

    afterEach(() => {
      jest.useRealTimers();
    });

    it("falls back to existing absolute behavior for invalid dates", () => {
      expect(() => formatUpdatedAtRelative("not-a-date")).toThrow();
    });

    it("formats very recent updates as Recently", () => {
      const secondsAgo = new Date(now.getTime() - 30_000).toISOString();
      expect(formatUpdatedAtRelative(secondsAgo)).toBe("Updated Recently");
    });

    it("formats updates under one hour using relative minutes", () => {
      const fiveMinutesAgo = new Date(now.getTime() - 5 * 60_000).toISOString();
      expect(formatUpdatedAtRelative(fiveMinutesAgo)).toMatch(
        /^Updated\s+5\s+(?:min\.?|minute)s?\s+ago$/i,
      );
    });

    it("formats updates under one day using relative hours", () => {
      const oneHourAgo = new Date(now.getTime() - 60 * 60_000).toISOString();
      expect(formatUpdatedAtRelative(oneHourAgo)).toMatch(
        /^Updated\s+1\s+(?:hr\.?|hour)s?\s+ago$/i,
      );
    });

    it("formats yesterday as a day label", () => {
      const yesterday = new Date(now.getTime() - 25 * 60 * 60_000).toISOString();
      expect(formatUpdatedAtRelative(yesterday)).toBe("Updated yesterday");
    });

    it("falls back to existing absolute formatter for older dates", () => {
      const oldDate = "2026-05-20T12:00:00.000Z";
      expect(formatUpdatedAtRelative(oldDate)).toBe(`Updated ${formatDateTime(oldDate)}`);
    });

    it("falls back to existing absolute formatter when Intl.RelativeTimeFormat is unavailable", () => {
      const oldRelativeTimeFormat = Intl.RelativeTimeFormat;
      Object.defineProperty(Intl, "RelativeTimeFormat", {
        configurable: true,
        value: undefined,
      });

      try {
        const value = new Date(now.getTime() - 5 * 60_000).toISOString();
        expect(formatUpdatedAtRelative(value)).toBe(`Updated ${formatDateTime(value)}`);
      } finally {
        Object.defineProperty(Intl, "RelativeTimeFormat", {
          configurable: true,
          value: oldRelativeTimeFormat,
        });
      }
    });
  });
});
