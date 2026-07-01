import {
  formatWaitingLabel,
  formatWaitingResultLabel,
  getUpcomingInterviewTiming,
  getWaitingUrgencyTone,
  getWholeCalendarDaysBetween,
  joinMetadata,
} from "./dashboard-card-formatters";

const localDateTime = (
  year: number,
  month: number,
  day: number,
  hour = 0,
  minute = 0,
): string => new Date(year, month, day, hour, minute).toISOString();

describe("dashboard card formatters", () => {
  describe("getWholeCalendarDaysBetween", () => {
    it("counts a local date boundary even when less than 24 hours elapsed", () => {
      const from = localDateTime(2026, 0, 1, 23);
      const now = new Date(2026, 0, 2, 1);

      expect(getWholeCalendarDaysBetween(from, now)).toBe(1);
    });

    it("counts calendar dates rather than complete 24-hour periods", () => {
      const from = localDateTime(2026, 0, 1, 0);
      const now = new Date(2026, 0, 2, 23, 59);

      expect(getWholeCalendarDaysBetween(from, now)).toBe(1);
    });
  });

  it("formats waiting labels", () => {
    expect(formatWaitingLabel(37)).toBe("Waiting 37 days");
    expect(formatWaitingResultLabel(14)).toBe("Waiting result 14 days");
  });

  it.each([3, 4, 5])(
    "keeps application urgency yellow when threshold 3 and maximum %i have a narrow spread",
    (maxDays) => {
      expect(getWaitingUrgencyTone(maxDays, 3, maxDays)).toBe("yellow");
    },
  );

  it("progresses application urgency across a wide spread", () => {
    expect(getWaitingUrgencyTone(3, 3, 10)).toBe("yellow");
    expect(getWaitingUrgencyTone(5, 3, 10)).toBe("amber");
    expect(getWaitingUrgencyTone(7, 3, 10)).toBe("orange");
    expect(getWaitingUrgencyTone(10, 3, 10)).toBe("red");
  });

  it.each([6, 7])(
    "keeps interview urgency yellow when threshold 5 and maximum %i have a narrow spread",
    (maxDays) => {
      expect(getWaitingUrgencyTone(maxDays, 5, maxDays)).toBe("yellow");
    },
  );

  it("progresses interview urgency across a wide spread", () => {
    expect(getWaitingUrgencyTone(5, 5, 20)).toBe("yellow");
    expect(getWaitingUrgencyTone(9, 5, 20)).toBe("amber");
    expect(getWaitingUrgencyTone(13, 5, 20)).toBe("orange");
    expect(getWaitingUrgencyTone(20, 5, 20)).toBe("red");
  });

  it("formats upcoming interview timing in local time", () => {
    const now = new Date(2026, 5, 8, 10);

    expect(getUpcomingInterviewTiming(localDateTime(2026, 5, 8, 14), now)).toEqual({
      label: "Today at 14:00",
      tone: "today",
    });
    expect(getUpcomingInterviewTiming(localDateTime(2026, 5, 9, 9), now)).toEqual({
      label: "Tomorrow",
      tone: "tomorrow",
    });
    expect(getUpcomingInterviewTiming(localDateTime(2026, 5, 13, 9), now)).toEqual({
      label: "In 5 days",
      tone: "future",
    });
  });

  it("joins only available metadata", () => {
    expect(joinMetadata(["London", "Remote"])).toBe("London · Remote");
    expect(joinMetadata(["London", null])).toBe("London");
    expect(joinMetadata([undefined, "Remote"])).toBe("Remote");
    expect(joinMetadata([null, undefined])).toBeNull();
  });
});
