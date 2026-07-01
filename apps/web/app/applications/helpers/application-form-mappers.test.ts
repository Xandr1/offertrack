import { Application } from "@/lib/api";
import { ApplicationFormState } from "../models/application-form-model";
import {
  toCreatePayload,
  toFormState,
  toReplacePayload,
} from "./application-form-mappers";

describe("application-form-mappers", () => {
  it("maps create payload with trimmed values and optional nulls", () => {
    const form: ApplicationFormState = {
      appliedAt: "2026-03-12",
      companyName: "  Acme  ",
      jobUrl: "  ",
      location: "  ",
      notes: "",
      positionTitle: "  Frontend Engineer ",
      stage: "applied",
      workMode: "",
    };

    expect(toCreatePayload(form)).toEqual({
      appliedAt: "2026-03-12T00:00:00.000Z",
      companyName: "Acme",
      jobUrl: null,
      location: null,
      notes: null,
      positionTitle: "Frontend Engineer",
      stage: "applied",
      workMode: null,
    });
  });

  it("maps replace payload and keeps nullable fields explicit", () => {
    const form: ApplicationFormState = {
      appliedAt: "",
      companyName: " Acme ",
      jobUrl: " google.com/careers/job-00001 ",
      location: " Warsaw ",
      notes: "  ",
      positionTitle: " Engineer ",
      stage: "interviewing",
      workMode: "remote",
    };

    expect(toReplacePayload(form)).toEqual({
      appliedAt: null,
      companyName: "Acme",
      jobUrl: "https://google.com/careers/job-00001",
      location: "Warsaw",
      notes: null,
      positionTitle: "Engineer",
      stage: "interviewing",
      workMode: "remote",
    });
  });

  it("maps API application to form state", () => {
    const application: Application = {
      appliedAt: "2026-01-08T00:00:00.000Z",
      companyName: "Acme",
      createdAt: "2026-01-08T10:00:00.000Z",
      id: "app-1",
      jobUrl: "https://example.com/jobs/1",
      location: null,
      followedUpAt: null,
      lastInterview: null,
      nextInterview: null,
      notes: null,
      positionTitle: "Engineer",
      stage: "offer",
      updatedAt: "2026-01-09T10:00:00.000Z",
      workMode: null,
    };

    expect(toFormState(application)).toEqual({
      appliedAt: "2026-01-08",
      companyName: "Acme",
      jobUrl: "https://example.com/jobs/1",
      location: "",
      notes: "",
      positionTitle: "Engineer",
      stage: "offer",
      workMode: "",
    });
  });
});
