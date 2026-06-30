import {
  applicationsPageSchema,
  applicationBoardSchema,
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

  it("parses password reset success responses", () => {
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
      followUpAfterApplyingDays: 10,
      upcomingInterviewDays: 14,
      followUpAfterInterviewDays: 4,
      applicationsToFollowUp: {
        totalCount: 1,
        nextOffset: 1,
        hasMore: false,
        items: [{
          applicationId: "app-2",
          companyName: "Globex",
          positionTitle: "Platform Engineer",
          stage: "applied",
          jobUrl: null,
          location: null,
          workMode: null,
          appliedAt: "2026-05-20T10:00:00Z",
          updatedAt: "2026-05-21T10:00:00Z",
        }],
      },
      upcomingInterviews: {
        totalCount: 1,
        nextOffset: 1,
        hasMore: false,
        items: [{
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
        }],
      },
      interviewsToFollowUp: {
        totalCount: 1,
        nextOffset: 1,
        hasMore: false,
        items: [{
          applicationId: "app-4",
          interviewId: "interview-2",
          companyName: "Umbrella",
          positionTitle: "Staff Engineer",
          jobUrl: "https://example.com/umbrella",
          location: null,
          workMode: null,
          scheduledAt: "2026-06-01T09:00:00Z",
          interviewType: "hr",
          status: "scheduled",
        }],
      },
    });

    expect(parsed.applicationsToFollowUp.items[0].stage).toBe("applied");
    expect(parsed.followUpAfterApplyingDays).toBe(10);
    expect(parsed.upcomingInterviewDays).toBe(14);
    expect(parsed.followUpAfterInterviewDays).toBe(4);
    expect(parsed.upcomingInterviews.items[0].interviewType).toBe("technical");
    expect(parsed.interviewsToFollowUp.items[0].status).toBe("scheduled");
  });
});

describe("applicationsPageSchema", () => {
  it("parses paginated applications responses", () => {
    const parsed = applicationsPageSchema.parse({
      items: [
        {
          appliedAt: null,
          companyName: "Acme",
          createdAt: "2026-06-01T10:00:00Z",
          id: "app-1",
          jobUrl: null,
          location: null,
          followedUpAt: null,
          lastInterview: null,
          nextInterview: null,
          notes: null,
          positionTitle: "Backend Engineer",
          stage: "applied",
          updatedAt: "2026-06-01T10:00:00Z",
          workMode: null,
        },
      ],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });

    expect(parsed.items[0].stage).toBe("applied");
    expect(parsed.totalItems).toBe(1);
  });
});

describe("applicationBoardSchema", () => {
  it("parses board columns and pagination metadata", () => {
    const parsed = applicationBoardSchema.parse({
      columns: [
        {
          stage: "applied",
          totalCount: 21,
          items: [],
          nextOffset: 20,
          hasMore: true,
        },
      ],
    });

    expect(parsed.columns[0]).toMatchObject({
      stage: "applied",
      totalCount: 21,
      nextOffset: 20,
      hasMore: true,
    });
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
