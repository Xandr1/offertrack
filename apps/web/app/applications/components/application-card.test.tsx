/** @jest-environment jsdom */

import { render, screen } from "@testing-library/react";
import type { Application } from "@/lib/api";
import { ApplicationCard } from "./application-card";

const application = (overrides: Partial<Application>): Application => ({
  id: "app-1",
  companyName: "Acme",
  positionTitle: "Engineer",
  jobUrl: null,
  location: null,
  workMode: null,
  stage: "interviewing",
  notes: null,
  appliedAt: null,
  followedUpAt: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  nextInterview: null,
  lastInterview: null,
  ...overrides,
});

const renderCard = (value: Application) => render(
  <ApplicationCard
    application={value}
    isDeleting={false}
    isUpdatingInterviewStatus={false}
    isUpdatingStage={false}
    onDelete={jest.fn()}
    onEdit={jest.fn()}
    onNextInterviewStatusChange={jest.fn()}
    onStageChange={jest.fn()}
  />,
);

describe("ApplicationCard interview summary", () => {
  it("prefers next and shows a status select for scheduled interviews", () => {
    renderCard(application({
      nextInterview: { id: "next", type: "technical", status: "scheduled", scheduledAt: "2026-07-01T10:00:00Z" },
      lastInterview: { id: "last", type: "hr", status: "passed", scheduledAt: "2026-06-01T10:00:00Z" },
    }));

    expect(screen.getByText(/Next interview: Technical/)).toBeTruthy();
    expect(screen.getAllByRole("combobox")).toHaveLength(2);
  });

  it("shows last and hides the status select for terminal outcomes", () => {
    renderCard(application({
      lastInterview: { id: "last", type: "hr", status: "rejected", scheduledAt: "2026-06-01T10:00:00Z" },
    }));

    expect(screen.getByText(/Last interview: HR/)).toBeTruthy();
    expect(screen.getAllByRole("combobox")).toHaveLength(1);
  });
});
