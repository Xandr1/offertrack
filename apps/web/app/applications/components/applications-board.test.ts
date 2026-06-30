/** @jest-environment jsdom */

import React from "react";
import { render, screen } from "@testing-library/react";
import type { Application, ApplicationBoard } from "@/lib/api";
import { ApplicationsBoard } from "./applications-board";

const application: Application = {
  appliedAt: null,
  companyName: "Acme",
  createdAt: "2026-01-01T00:00:00Z",
  id: "app-1",
  jobUrl: null,
  location: null,
  followedUpAt: null,
  lastInterview: null,
  nextInterview: {
    id: "interview-1",
    type: "technical",
    status: "scheduled",
    scheduledAt: "2026-06-30T14:00:00Z",
  },
  notes: null,
  positionTitle: "Engineer",
  stage: "applied",
  updatedAt: "2026-01-01T00:00:00Z",
  workMode: null,
};

const board: ApplicationBoard = {
  columns: [
    {
      stage: "initial",
      totalCount: 0,
      items: [],
      nextOffset: 0,
      hasMore: false,
    },
    {
      stage: "applied",
      totalCount: 2,
      items: [application],
      nextOffset: 1,
      hasMore: true,
    },
  ],
};

describe("ApplicationsBoard", () => {
  it("shows loaded counts and disables load more during a stage update", () => {
    render(
      React.createElement(ApplicationsBoard, {
        board,
        errorMessage: null,
        isLoading: false,
        isStageUpdatePending: true,
        loadMoreState: {},
        onDelete: jest.fn(),
        onDragEnd: jest.fn(),
        onEdit: jest.fn(),
        onLoadMore: jest.fn(),
        onRetry: jest.fn(),
      }),
    );

    expect(screen.getByText("Showing 0 of 0")).toBeTruthy();
    expect(screen.getByText("Showing 1 of 2")).toBeTruthy();
    expect(
      (screen.getByRole("button", { name: "Load more" }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);
    expect(screen.getByText(/📅 Technical/)).toBeTruthy();
  });
});
