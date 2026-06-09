import {
  getSettingsFormValidationError,
  toSettingsFormState,
  toUpdateSettingsPayload,
} from "./settings-form-mappers";

describe("settings-form-mappers", () => {
  it("maps loaded settings into form state", () => {
    const form = toSettingsFormState({
      followUpAfterApplyingDays: 10,
      upcomingInterviewDays: 14,
      followUpAfterInterviewDays: 4,
      targetRole: "Platform Engineer",
    });

    expect(form).toEqual({
      followUpAfterApplyingDays: "10",
      upcomingInterviewDays: "14",
      followUpAfterInterviewDays: "4",
      targetRole: "Platform Engineer",
    });
  });

  it("submits normalized payload", () => {
    const payload = toUpdateSettingsPayload({
      followUpAfterApplyingDays: " 8 ",
      upcomingInterviewDays: "12",
      followUpAfterInterviewDays: "3",
      targetRole: "  Backend Engineer  ",
    });

    expect(payload).toEqual({
      followUpAfterApplyingDays: 8,
      upcomingInterviewDays: 12,
      followUpAfterInterviewDays: 3,
      targetRole: "Backend Engineer",
    });
  });

  it("normalizes blank target role to null", () => {
    const payload = toUpdateSettingsPayload({
      followUpAfterApplyingDays: "7",
      upcomingInterviewDays: "7",
      followUpAfterInterviewDays: "2",
      targetRole: "   ",
    });

    expect(payload.targetRole).toBeNull();
  });

  it("returns validation error for out-of-range values", () => {
    expect(
      getSettingsFormValidationError({
        followUpAfterApplyingDays: "0",
        upcomingInterviewDays: "7",
        followUpAfterInterviewDays: "2",
        targetRole: "",
      }),
    ).toBe("Follow up after applying must be between 1 and 60 days.");
  });
});
