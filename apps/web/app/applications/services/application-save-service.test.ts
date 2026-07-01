import { Application, ApplicationInterview, ApplicationWithInterviews } from "@/lib/api";
import { InterviewDraftRow } from "../models/interview-row-model";
import { saveApplicationWithInterviews } from "./application-save-service";

const makeApplication = (id: string): Application => ({
  appliedAt: null,
  companyName: "Acme",
  createdAt: "2026-01-01T00:00:00.000Z",
  id,
  jobUrl: null,
  location: null,
  followedUpAt: null,
  lastInterview: null,
  nextInterview: null,
  notes: null,
  positionTitle: "Engineer",
  stage: "applied",
  updatedAt: "2026-01-01T00:00:00.000Z",
  workMode: null,
});

const makeInterview = (id: string): ApplicationInterview => ({
  applicationId: "app-1",
  createdAt: "2026-01-01T00:00:00.000Z",
  followedUpAt: null,
  id,
  scheduledAt: null,
  status: "initial",
  type: "technical",
  updatedAt: "2026-01-01T00:00:00.000Z",
});

const makeAggregateResponse = (applicationId: string): ApplicationWithInterviews => ({
  application: makeApplication(applicationId),
  interviews: [makeInterview("int-1")],
});

const makeInterviewRow = (
  interviewId: string | null,
  rowId: string,
  overrides: Partial<InterviewDraftRow> = {},
): InterviewDraftRow => ({
  interviewId,
  rowId,
  scheduledAt: "",
  status: "initial",
  type: "technical",
  ...overrides,
});

describe("application-save-service", () => {
  it("create save calls one aggregate API request and returns its interviews", async () => {
    const deps = {
      createApplication: jest
        .fn()
        .mockResolvedValue(makeAggregateResponse("app-1")),
      replaceApplication: jest.fn(),
    };

    const result = await saveApplicationWithInterviews(
      {
        form: {
          appliedAt: "2026-01-12",
          companyName: " Acme ",
          jobUrl: " google.com/careers/job-00001 ",
          location: "",
          notes: "",
          positionTitle: " Engineer ",
          stage: "applied",
          workMode: "",
        },
        mode: "create",
        rows: [makeInterviewRow(null, "row-1", { type: "technical" })],
      },
      deps,
    );

    expect(deps.createApplication).toHaveBeenCalledTimes(1);
    expect(deps.replaceApplication).not.toHaveBeenCalled();
    expect(deps.createApplication).toHaveBeenCalledWith({
      appliedAt: "2026-01-12T00:00:00.000Z",
      companyName: "Acme",
      interviews: [
        {
          scheduledAt: null,
          status: "initial",
          type: "technical",
        },
      ],
      jobUrl: "https://google.com/careers/job-00001",
      location: null,
      notes: null,
      positionTitle: "Engineer",
      stage: "applied",
      workMode: null,
    });
    expect(result).toEqual({
      application: makeApplication("app-1"),
      applicationId: "app-1",
      interviews: [makeInterview("int-1")],
      mode: "create",
    });
  });

  it("edit save calls one aggregate API request and returns its interviews", async () => {
    const deps = {
      createApplication: jest.fn(),
      replaceApplication: jest
        .fn()
        .mockResolvedValue(makeAggregateResponse("app-1")),
    };

    const result = await saveApplicationWithInterviews(
      {
        applicationId: "app-1",
        form: {
          appliedAt: "",
          companyName: "Acme",
          jobUrl: "",
          location: "",
          notes: "",
          positionTitle: "Engineer",
          stage: "interviewing",
          workMode: "",
        },
        mode: "edit",
        rows: [
          makeInterviewRow("int-1", "row-1", { status: "scheduled" }),
          makeInterviewRow(null, "row-2", { type: "hr" }),
        ],
      },
      deps,
    );

    expect(deps.replaceApplication).toHaveBeenCalledTimes(1);
    expect(deps.createApplication).not.toHaveBeenCalled();
    expect(deps.replaceApplication).toHaveBeenCalledWith("app-1", {
      appliedAt: null,
      companyName: "Acme",
      interviews: [
        {
          id: "int-1",
          scheduledAt: null,
          status: "scheduled",
          type: "technical",
        },
        {
          scheduledAt: null,
          status: "initial",
          type: "hr",
        },
      ],
      jobUrl: null,
      location: null,
      notes: null,
      positionTitle: "Engineer",
      stage: "interviewing",
      workMode: null,
    });
    expect(result).toEqual({
      application: makeApplication("app-1"),
      applicationId: "app-1",
      interviews: [makeInterview("int-1")],
      mode: "edit",
    });
  });

  it("omits rows still in pending undo from final PUT payload", async () => {
    const deps = {
      createApplication: jest.fn(),
      replaceApplication: jest
        .fn()
        .mockResolvedValue(makeAggregateResponse("app-1")),
    };

    await saveApplicationWithInterviews(
      {
        applicationId: "app-1",
        form: {
          appliedAt: "",
          companyName: "Acme",
          jobUrl: "",
          location: "",
          notes: "",
          positionTitle: "Engineer",
          stage: "interviewing",
          workMode: "",
        },
        mode: "edit",
        pendingUndoRowIds: ["row-omit"],
        rows: [
          makeInterviewRow("int-1", "row-keep", { status: "scheduled" }),
          makeInterviewRow("int-2", "row-omit", { status: "scheduled" }),
        ],
      },
      deps,
    );

    expect(deps.replaceApplication).toHaveBeenCalledWith("app-1", {
      appliedAt: null,
      companyName: "Acme",
      interviews: [
        {
          id: "int-1",
          scheduledAt: null,
          status: "scheduled",
          type: "technical",
        },
      ],
      jobUrl: null,
      location: null,
      notes: null,
      positionTitle: "Engineer",
      stage: "interviewing",
      workMode: null,
    });
  });

  it("includes undo-restored rows in final PUT payload", async () => {
    const deps = {
      createApplication: jest.fn(),
      replaceApplication: jest
        .fn()
        .mockResolvedValue(makeAggregateResponse("app-1")),
    };

    await saveApplicationWithInterviews(
      {
        applicationId: "app-1",
        form: {
          appliedAt: "",
          companyName: "Acme",
          jobUrl: "",
          location: "",
          notes: "",
          positionTitle: "Engineer",
          stage: "interviewing",
          workMode: "",
        },
        mode: "edit",
        pendingUndoRowIds: [],
        rows: [makeInterviewRow("int-3", "row-restored", { type: "hr" })],
      },
      deps,
    );

    expect(deps.replaceApplication).toHaveBeenCalledWith("app-1", {
      appliedAt: null,
      companyName: "Acme",
      interviews: [
        {
          id: "int-3",
          scheduledAt: null,
          status: "initial",
          type: "hr",
        },
      ],
      jobUrl: null,
      location: null,
      notes: null,
      positionTitle: "Engineer",
      stage: "interviewing",
      workMode: null,
    });
  });

  it("propagates errors from aggregate save", async () => {
    const deps = {
      createApplication: jest.fn(),
      replaceApplication: jest
        .fn()
        .mockRejectedValue(new Error("replaceApplication failed")),
    };

    await expect(
      saveApplicationWithInterviews(
        {
          applicationId: "app-1",
          form: {
            appliedAt: "",
            companyName: "Acme",
            jobUrl: "",
            location: "",
            notes: "",
            positionTitle: "Engineer",
            stage: "interviewing",
            workMode: "",
          },
          mode: "edit",
          rows: [],
        },
        deps,
      ),
    ).rejects.toThrow("replaceApplication failed");

    expect(deps.createApplication).not.toHaveBeenCalled();
    expect(deps.replaceApplication).toHaveBeenCalledTimes(1);
  });
});
