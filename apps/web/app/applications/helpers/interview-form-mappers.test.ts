import { ApplicationInterview } from "@/lib/api";
import {
  toCreateInterviewPayload,
  toInterviewDraftRow,
  toReplaceInterviewPayload,
} from "./interview-form-mappers";

describe("interview-form-mappers", () => {
  it("maps API interview to draft row", () => {
    const interview: ApplicationInterview = {
      applicationId: "app-1",
      createdAt: "2026-01-08T10:00:00.000Z",
      followedUpAt: null,
      id: "int-1",
      scheduledAt: "2026-01-08T11:30:00.000Z",
      status: "scheduled",
      type: "technical",
      updatedAt: "2026-01-08T12:00:00.000Z",
    };

    const row = toInterviewDraftRow(interview);

    expect(row.interviewId).toBe("int-1");
    expect(row.rowId).toBe("int-1");
    expect(row.status).toBe("scheduled");
    expect(row.type).toBe("technical");
    expect(row.scheduledAt).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/);
  });

  it("creates payload from valid row", () => {
    const row = {
      interviewId: null,
      rowId: "row-1",
      scheduledAt: "2026-01-09T08:45",
      status: "initial" as const,
      type: "hr" as const,
    };

    expect(toCreateInterviewPayload(row)).toEqual({
      scheduledAt: new Date("2026-01-09T08:45").toISOString(),
      status: "initial",
      type: "hr",
    });
  });

  it("keeps an undated Other row valid for create and replace", () => {
    const row = {
      interviewId: "int-1",
      rowId: "row-1",
      scheduledAt: "",
      status: "initial" as const,
      type: "other" as const,
    };

    expect(toCreateInterviewPayload(row)).toEqual({
      scheduledAt: null,
      status: "initial",
      type: "other",
    });
    expect(toReplaceInterviewPayload(row)).toEqual({
      id: "int-1",
      scheduledAt: null,
      status: "initial",
      type: "other",
    });
  });
});
