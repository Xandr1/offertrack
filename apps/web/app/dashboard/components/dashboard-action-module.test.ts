import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { DashboardActionModule } from "./dashboard-action-module";

describe("DashboardActionModule", () => {
  it("renders application items and actions", () => {
    const html = renderToStaticMarkup(
      React.createElement(DashboardActionModule, {
        title: "Drafts to apply",
        count: 4,
        helperText: "Applications still waiting to be applied.",
        isLoading: false,
        kind: "applications",
        viewAllHref: "/applications?stage=initial",
        items: [
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
      }),
    );

    expect(html).toContain("Drafts to apply");
    expect(html).toContain("Acme");
    expect(html).toContain("Backend Engineer");
    expect(html).toContain("View in applications");
    expect(html).toContain("href=\"/applications\"");
    expect(html).toContain("View all");
    expect(html).toContain("href=\"/applications?stage=initial\"");
    expect(html).toContain("Open job post");
    expect(html).toContain("href=\"https://example.com/job\"");
  });

  it("renders interview items", () => {
    const html = renderToStaticMarkup(
      React.createElement(DashboardActionModule, {
        title: "Upcoming interviews",
        count: 1,
        helperText: "Scheduled interviews in the next 7 days.",
        isLoading: false,
        kind: "interviews",
        viewAllHref: "/applications?stage=interviewing",
        items: [
          {
            applicationId: "app-2",
            interviewId: "interview-1",
            companyName: "Globex",
            positionTitle: "Platform Engineer",
            jobUrl: null,
            location: "Warsaw",
            workMode: "hybrid",
            scheduledAt: "2026-06-08T09:00:00Z",
            interviewType: "technical",
            status: "scheduled",
          },
        ],
      }),
    );

    expect(html).toContain("Upcoming interviews");
    expect(html).toContain("Globex");
    expect(html).toContain("Technical");
    expect(html).toContain("Scheduled");
  });

  it("renders empty states", () => {
    const html = renderToStaticMarkup(
      React.createElement(DashboardActionModule, {
        title: "Applications to follow up",
        count: 0,
        helperText: "Applied applications that may need a follow-up.",
        isLoading: false,
        kind: "applications",
        viewAllHref: "/applications?stage=applied",
        items: [],
      }),
    );

    expect(html).toContain("All clear — no action needed");
  });

  it("renders the job URL action only when present", () => {
    const html = renderToStaticMarkup(
      React.createElement(DashboardActionModule, {
        title: "Applications to follow up",
        count: 1,
        helperText: "Applied at least 7 days ago.",
        isLoading: false,
        kind: "applications",
        viewAllHref: "/applications?stage=applied",
        items: [
          {
            applicationId: "app-3",
            companyName: "Initech",
            positionTitle: "Java Engineer",
            stage: "applied",
            jobUrl: null,
            location: null,
            workMode: null,
            appliedAt: "2026-05-20T10:00:00Z",
            updatedAt: "2026-05-21T10:00:00Z",
          },
        ],
      }),
    );

    expect(html).toContain("View in applications");
    expect(html).not.toContain("Open job post");
  });
});
