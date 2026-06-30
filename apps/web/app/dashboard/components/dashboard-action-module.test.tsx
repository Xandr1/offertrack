import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { DashboardActionModule } from "./dashboard-action-module";

const baseProps = {
  count: 1,
  errorMessage: null,
  hasMore: false,
  isLoading: false,
  isLoadingMore: false,
  pendingIds: new Set<string>(),
  onLoadMore: jest.fn(),
  onMarkFollowedUp: jest.fn(),
  onUndo: jest.fn(),
};

describe("DashboardActionModule", () => {
  it("renders one concrete application link and follow-up action", () => {
    const html = renderToStaticMarkup(
      <DashboardActionModule
        {...baseProps}
        helperText="Applied at least 7 days ago."
        items={[{
          applicationId: "app-1",
          companyName: "Acme",
          positionTitle: "Backend Engineer",
          stage: "applied",
          jobUrl: "https://example.com/job",
          location: "Remote",
          workMode: "remote",
          appliedAt: null,
          updatedAt: "2026-06-01T10:00:00Z",
        }]}
        kind="applications"
        title="Applications to follow up"
      />,
    );

    expect(html).toContain("Mark followed up");
    expect(html).toContain('href="/applications?id=app-1"');
    expect(html).not.toContain("View all");
    expect(html).not.toContain("Open job post");
  });

  it("does not show a follow-up action for upcoming interviews", () => {
    const html = renderToStaticMarkup(
      <DashboardActionModule
        {...baseProps}
        helperText="Coming soon."
        items={[{
          applicationId: "app-2",
          interviewId: "int-1",
          companyName: "Globex",
          positionTitle: "Engineer",
          jobUrl: null,
          location: null,
          workMode: null,
          scheduledAt: "2026-06-08T09:00:00Z",
          interviewType: "technical",
          status: "scheduled",
        }]}
        kind="upcoming-interviews"
        title="Upcoming interviews"
      />,
    );

    expect(html).toContain("Upcoming interviews");
    expect(html).not.toContain("Mark followed up");
  });

  it("renders pending undo state and load more", () => {
    const html = renderToStaticMarkup(
      <DashboardActionModule
        {...baseProps}
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
          scheduledAt: "2026-06-01T09:00:00Z",
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
