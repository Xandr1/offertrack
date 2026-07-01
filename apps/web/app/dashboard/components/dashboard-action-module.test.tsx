import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { DashboardActionModule } from "./dashboard-action-module";

const now = new Date(2026, 5, 8, 12);
const localDateTime = (
  year: number,
  month: number,
  day: number,
  hour = 9,
): string => new Date(year, month, day, hour).toISOString();

const baseProps = {
  count: 1,
  errorMessage: null,
  hasMore: false,
  isLoading: false,
  isLoadingMore: false,
  now,
  pendingIds: new Set<string>(),
  onLoadMore: jest.fn(),
  onMarkFollowedUp: jest.fn(),
  onUndo: jest.fn(),
};

describe("DashboardActionModule", () => {
  it("renders the compact application follow-up card", () => {
    const html = renderToStaticMarkup(
      <DashboardActionModule
        {...baseProps}
        followUpAfterApplyingDays={7}
        helperText="Applied at least 7 days ago."
        items={[{
          applicationId: "app-1",
          companyName: "Google",
          positionTitle: "Senior SWE",
          stage: "applied",
          jobUrl: "https://example.com/job",
          location: "Mountain View",
          workMode: "onsite",
          appliedAt: null,
          createdAt: localDateTime(2026, 4, 2),
          updatedAt: localDateTime(2026, 5, 7),
        }]}
        kind="applications"
        title="Applications to follow up"
      />,
    );

    const itemHtml = html.slice(html.indexOf("<li"));
    expect(itemHtml).toContain("Waiting 37 days");
    expect(itemHtml).toContain("Google");
    expect(itemHtml).toContain("Senior SWE");
    expect(itemHtml).toContain("Mountain View · Onsite");
    expect(itemHtml).toContain("View application");
    expect(itemHtml).toContain("Mark followed up");
    expect(itemHtml).toContain('href="/applications?id=app-1"');
    expect(itemHtml).not.toContain("Updated");
    expect(itemHtml).not.toContain(">Applied<");
    expect(itemHtml).not.toContain("Open job post");
  });

  it("renders interview type and local timing for upcoming interviews", () => {
    const html = renderToStaticMarkup(
      <DashboardActionModule
        {...baseProps}
        helperText="Coming soon."
        items={[{
          applicationId: "app-2",
          interviewId: "int-1",
          companyName: "SoftHouseGroup",
          positionTitle: "Tech Lead (Angular/Node.js)",
          jobUrl: null,
          location: "Vinnytsia",
          workMode: "remote",
          scheduledAt: localDateTime(2026, 5, 8, 14),
          interviewType: "hiring_manager",
          status: "scheduled",
        }]}
        kind="upcoming-interviews"
        title="Upcoming interviews"
      />,
    );

    const itemHtml = html.slice(html.indexOf("<li"));
    expect(itemHtml).toContain("Hiring Manager");
    expect(itemHtml).toContain("Today at 14:00");
    expect(itemHtml).toContain("SoftHouseGroup");
    expect(itemHtml).toContain("Tech Lead (Angular/Node.js)");
    expect(itemHtml).toContain("Vinnytsia · Remote");
    expect(itemHtml).toContain("View application");
    expect(itemHtml).not.toContain("Scheduled");
    expect(itemHtml).not.toContain("2026");
    expect(itemHtml).not.toContain("Mark followed up");
  });

  it("renders the compact interview follow-up card", () => {
    const html = renderToStaticMarkup(
      <DashboardActionModule
        {...baseProps}
        followUpAfterInterviewDays={5}
        helperText="Waiting on an outcome."
        items={[{
          applicationId: "app-3",
          interviewId: "int-2",
          companyName: "Amazon",
          positionTitle: "Principal Software Development Engineer",
          jobUrl: null,
          location: "London, UK",
          workMode: "onsite",
          scheduledAt: localDateTime(2026, 4, 25),
          interviewType: "technical",
          status: "scheduled",
        }]}
        kind="interviews-to-follow-up"
        title="Interviews to follow up"
      />,
    );

    const itemHtml = html.slice(html.indexOf("<li"));
    expect(itemHtml).toContain("Technical");
    expect(itemHtml).toContain("Waiting result 14 days");
    expect(itemHtml).toContain("Amazon");
    expect(itemHtml).toContain("Principal Software Development Engineer");
    expect(itemHtml).toContain("London, UK · Onsite");
    expect(itemHtml).toContain("View application");
    expect(itemHtml).toContain("Mark followed up");
    expect(itemHtml).not.toContain("Scheduled");
    expect(itemHtml).not.toContain("2026");
  });

  it("renders pending undo state and load more", () => {
    const html = renderToStaticMarkup(
      <DashboardActionModule
        {...baseProps}
        followUpAfterInterviewDays={5}
        hasMore
        helperText="Waiting on an outcome."
        items={[{
          applicationId: "app-2",
          interviewId: "int-1",
          companyName: "Globex",
          positionTitle: "Engineer",
          jobUrl: null,
          location: null,
          workMode: null,
          scheduledAt: localDateTime(2026, 5, 1),
          interviewType: "technical",
          status: "scheduled",
        }]}
        kind="interviews-to-follow-up"
        pendingIds={new Set(["int-1"])}
        title="Interviews to follow up"
      />,
    );

    expect(html).toContain("Marked followed up");
    expect(html).toContain("Undo");
    expect(html).toContain("Load more");
  });
});
