import {
  dashboardSummarySchema,
  genericSuccessResponseSchema,
  registerResponseSchema,
  settingsSchema,
  verifyEmailResponseSchema,
} from "./schemas";

describe("auth schemas", () => {
  it("parses email verification auth responses", () => {
    expect(
      registerResponseSchema.parse({ emailVerificationRequired: true })
        .emailVerificationRequired,
    ).toBe(true);
    expect(verifyEmailResponseSchema.parse({ verified: true }).verified).toBe(
      true,
    );
    expect(genericSuccessResponseSchema.parse({ ok: true }).ok).toBe(true);
  });
});

describe("dashboardSummarySchema", () => {
  it("parses the dashboard action modules response", () => {
    const parsed = dashboardSummarySchema.parse({
      activeProcesses: 4,
      needsAttention: 6,
      interviewing: 2,
      offers: 1,
      rejected: 3,
      draftsToApplyCount: 1,
      applicationsToFollowUpCount: 1,
      upcomingInterviewsCount: 1,
      interviewsToFollowUpCount: 1,
      followUpAfterApplyingDays: 10,
      upcomingInterviewDays: 14,
      followUpAfterInterviewDays: 4,
      draftsToApply: [
        {
          applicationId: "app-1",
          companyName: "Acme",
          positionTitle: "Backend Engineer",
          stage: "initial",
          jobUrl: "https://example.com/job",
          location: "Remote",
          workMode: "remote",
          appliedAt: null,
          updatedAt: "2026-06-01T10:00:00Z",
        },
      ],
      applicationsToFollowUp: [
        {
          applicationId: "app-2",
          companyName: "Globex",
          positionTitle: "Platform Engineer",
          stage: "applied",
          jobUrl: null,
          location: null,
          workMode: null,
          appliedAt: "2026-05-20T10:00:00Z",
          updatedAt: "2026-05-21T10:00:00Z",
        },
      ],
      upcomingInterviews: [
        {
          applicationId: "app-3",
          interviewId: "interview-1",
          companyName: "Initech",
          positionTitle: "Frontend Engineer",
          jobUrl: null,
          location: "Warsaw",
          workMode: "hybrid",
          scheduledAt: "2026-06-08T09:00:00Z",
          interviewType: "technical",
          status: "scheduled",
        },
      ],
      interviewsToFollowUp: [
        {
          applicationId: "app-4",
          interviewId: "interview-2",
          companyName: "Umbrella",
          positionTitle: "Staff Engineer",
          jobUrl: "https://example.com/umbrella",
          location: null,
          workMode: null,
          scheduledAt: "2026-06-01T09:00:00Z",
          interviewType: "hr",
          status: "completed",
        },
      ],
    });

    expect(parsed.draftsToApply[0].stage).toBe("initial");
    expect(parsed.draftsToApplyCount).toBe(1);
    expect(parsed.followUpAfterApplyingDays).toBe(10);
    expect(parsed.upcomingInterviewDays).toBe(14);
    expect(parsed.followUpAfterInterviewDays).toBe(4);
    expect(parsed.upcomingInterviews[0].interviewType).toBe("technical");
    expect(parsed.interviewsToFollowUp[0].status).toBe("completed");
  });
});

describe("settingsSchema", () => {
  it("parses the settings response", () => {
    const parsed = settingsSchema.parse({
      followUpAfterApplyingDays: 10,
      upcomingInterviewDays: 14,
      followUpAfterInterviewDays: 4,
      targetRole: "Platform Engineer",
    });

    expect(parsed.followUpAfterApplyingDays).toBe(10);
    expect(parsed.upcomingInterviewDays).toBe(14);
    expect(parsed.followUpAfterInterviewDays).toBe(4);
    expect(parsed.targetRole).toBe("Platform Engineer");
  });

  it("parses missing target role as null", () => {
    const parsed = settingsSchema.parse({
      followUpAfterApplyingDays: 7,
      upcomingInterviewDays: 7,
      followUpAfterInterviewDays: 2,
    });

    expect(parsed.targetRole).toBeNull();
  });
});
