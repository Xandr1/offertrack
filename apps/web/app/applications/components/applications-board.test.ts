/** @jest-environment jsdom */

import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
    expect(screen.getByText(/Next interview: Technical/)).toBeTruthy();
    expect(screen.getByText(/^Updated /).className).toContain("truncate");
    expect(screen.getByText(/^Updated /).className).toContain("block");
    expect(screen.getByText("Initial").closest("section")?.className).toContain(
      "xl:min-w-[272px]",
    );
    expect(screen.queryByRole("combobox")).toBeNull();
  });

  it("shows compact context for an undated scheduled next interview", () => {
    render(
      React.createElement(ApplicationsBoard, {
        board: {
          ...board,
          columns: board.columns.map((column) => ({
            ...column,
            items: column.items.map((item) => ({
              ...item,
              nextInterview: item.nextInterview
                ? { ...item.nextInterview, scheduledAt: null }
                : null,
            })),
          })),
        },
        errorMessage: null,
        isLoading: false,
        isStageUpdatePending: false,
        loadMoreState: {},
        onDelete: jest.fn(),
        onDragEnd: jest.fn(),
        onEdit: jest.fn(),
        onLoadMore: jest.fn(),
        onRetry: jest.fn(),
      }),
    );

    expect(screen.getByText("Next interview: Technical")).toBeTruthy();
    expect(screen.queryByRole("combobox")).toBeNull();
  });

  it("opens from the card body without conflating actions or drag handle", async () => {
    const onDelete = jest.fn();
    const onEdit = jest.fn();
    const user = userEvent.setup();
    render(
      React.createElement(ApplicationsBoard, {
        board,
        errorMessage: null,
        isLoading: false,
        isStageUpdatePending: false,
        loadMoreState: {},
        onDelete,
        onDragEnd: jest.fn(),
        onEdit,
        onLoadMore: jest.fn(),
        onRetry: jest.fn(),
      }),
    );

    await user.click(
      screen.getByRole("button", { name: "Open Acme application" }),
    );
    expect(onEdit).toHaveBeenCalledTimes(1);

    onEdit.mockClear();
    await user.click(screen.getByRole("button", { name: "Edit" }));
    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(onDelete).not.toHaveBeenCalled();

    onEdit.mockClear();
    await user.click(screen.getByRole("button", { name: "Delete" }));
    expect(onDelete).toHaveBeenCalledTimes(1);
    expect(onEdit).not.toHaveBeenCalled();

    await user.click(
      screen.getByRole("button", { name: "Move Acme application" }),
    );
    expect(onEdit).not.toHaveBeenCalled();
  });
});
