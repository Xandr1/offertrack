/** @jest-environment jsdom */

import React from "react";
import * as dndKit from "@dnd-kit/core";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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

const emptyBoard: ApplicationBoard = {
  ...board,
  columns: board.columns.map((column) => ({
    ...column,
    hasMore: false,
    items: [],
    nextOffset: 0,
    totalCount: 0,
  })),
};

describe("ApplicationsBoard", () => {
  it("shows one actionable Board-level empty state for filtered zero results", async () => {
    const onClearFilters = jest.fn();
    const user = userEvent.setup();
    render(
      React.createElement(ApplicationsBoard, {
        board: emptyBoard,
        errorMessage: null,
        hasActiveFilters: true,
        isLoading: false,
        isStageUpdatePending: false,
        loadMoreState: {},
        onClearFilters,
        onDelete: jest.fn(),
        onDragEnd: jest.fn(),
        onEdit: jest.fn(),
        onLoadMore: jest.fn(),
        onRetry: jest.fn(),
      }),
    );

    expect(screen.getByText("No matching applications")).toBeTruthy();
    expect(
      screen.getByText("Try changing or clearing the current filters."),
    ).toBeTruthy();
    expect(screen.queryByText("No applications")).toBeNull();
    expect(
      screen.queryByText(
        "Drag an application here when it reaches this stage.",
      ),
    ).toBeNull();

    await user.click(screen.getByRole("button", { name: "Clear filters" }));
    expect(onClearFilters).toHaveBeenCalledTimes(1);
  });

  it("renders the normal Board when active filters leave any visible result", () => {
    render(
      React.createElement(ApplicationsBoard, {
        board,
        errorMessage: null,
        hasActiveFilters: true,
        isLoading: false,
        isStageUpdatePending: false,
        loadMoreState: {},
        onClearFilters: jest.fn(),
        onDelete: jest.fn(),
        onDragEnd: jest.fn(),
        onEdit: jest.fn(),
        onLoadMore: jest.fn(),
        onRetry: jest.fn(),
      }),
    );

    expect(screen.queryByText("No matching applications")).toBeNull();
    expect(screen.getByRole("heading", { name: "Initial" })).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Applied" })).toBeTruthy();
    expect(screen.getByText("No applications")).toBeTruthy();
    expect(
      screen.getByRole("button", { name: "Open Acme application" }),
    ).toBeTruthy();
  });

  it("keeps unfiltered empty columns rendered with their drop instructions", () => {
    const useDroppableSpy = jest.spyOn(dndKit, "useDroppable");

    render(
      React.createElement(ApplicationsBoard, {
        board: emptyBoard,
        errorMessage: null,
        hasActiveFilters: false,
        isLoading: false,
        isStageUpdatePending: false,
        loadMoreState: {},
        onClearFilters: jest.fn(),
        onDelete: jest.fn(),
        onDragEnd: jest.fn(),
        onEdit: jest.fn(),
        onLoadMore: jest.fn(),
        onRetry: jest.fn(),
      }),
    );

    expect(screen.queryByText("No matching applications")).toBeNull();
    expect(screen.getAllByText("No applications")).toHaveLength(2);
    expect(
      screen.getAllByText(
        "Drag an application here when it reaches this stage.",
      ),
    ).toHaveLength(2);
    expect(
      screen.getByRole("heading", { name: "Initial" }).closest("section"),
    ).toBeTruthy();
    expect(
      screen.getByRole("heading", { name: "Applied" }).closest("section"),
    ).toBeTruthy();
    expect(useDroppableSpy).toHaveBeenCalledWith({ id: "initial" });
    expect(useDroppableSpy).toHaveBeenCalledWith({ id: "applied" });
    useDroppableSpy.mockRestore();
  });

  it("keeps loading and error states ahead of filtered-empty handling", () => {
    const baseProps = {
      board: emptyBoard,
      hasActiveFilters: true,
      isStageUpdatePending: false,
      loadMoreState: {},
      onClearFilters: jest.fn(),
      onDelete: jest.fn(),
      onDragEnd: jest.fn(),
      onEdit: jest.fn(),
      onLoadMore: jest.fn(),
      onRetry: jest.fn(),
    };
    const view = render(
      React.createElement(ApplicationsBoard, {
        ...baseProps,
        errorMessage: null,
        isLoading: true,
      }),
    );

    expect(screen.getByText("Loading board...")).toBeTruthy();
    expect(screen.queryByText("No matching applications")).toBeNull();

    view.rerender(
      React.createElement(ApplicationsBoard, {
        ...baseProps,
        errorMessage: "Board unavailable",
        isLoading: false,
      }),
    );
    expect(screen.getByText("Board unavailable")).toBeTruthy();
    expect(screen.queryByText("No matching applications")).toBeNull();
  });

  it("keeps cards openable while disabling drag, delete, and load more during a stage update", async () => {
    const onEdit = jest.fn();
    const user = userEvent.setup();
    render(
      React.createElement(ApplicationsBoard, {
        board,
        errorMessage: null,
        hasActiveFilters: false,
        isLoading: false,
        isStageUpdatePending: true,
        loadMoreState: {},
        onClearFilters: jest.fn(),
        onDelete: jest.fn(),
        onDragEnd: jest.fn(),
        onEdit,
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
    expect(
      (
        screen.getByRole("button", {
          name: "Open Acme application",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(false);
    expect(
      (
        screen.getByRole("button", {
          name: "Delete Acme application",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(true);

    await user.click(
      screen.getByRole("button", { name: "Open Acme application" }),
    );
    expect(onEdit).toHaveBeenCalledWith(application);
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
        hasActiveFilters: false,
        isLoading: false,
        isStageUpdatePending: false,
        loadMoreState: {},
        onClearFilters: jest.fn(),
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

  it("opens from the full card surface and isolates the delete action", async () => {
    const onDelete = jest.fn();
    const onDragEnd = jest.fn();
    const onEdit = jest.fn();
    const user = userEvent.setup();
    render(
      React.createElement(ApplicationsBoard, {
        board,
        errorMessage: null,
        hasActiveFilters: false,
        isLoading: false,
        isStageUpdatePending: false,
        loadMoreState: {},
        onClearFilters: jest.fn(),
        onDelete,
        onDragEnd,
        onEdit,
        onLoadMore: jest.fn(),
        onRetry: jest.fn(),
      }),
    );

    await user.click(
      screen.getByRole("button", { name: "Open Acme application" }),
    );
    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(onDragEnd).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: "Edit" })).toBeNull();

    onEdit.mockClear();
    await user.click(
      screen.getByRole("button", { name: "Delete Acme application" }),
    );
    expect(onDelete).toHaveBeenCalledTimes(1);
    expect(onEdit).not.toHaveBeenCalled();
  });

  it("keeps Enter for opening and Space for a real keyboard drag", async () => {
    const onDragEnd = jest.fn();
    const onEdit = jest.fn();
    const user = userEvent.setup();
    render(
      React.createElement(ApplicationsBoard, {
        board,
        errorMessage: null,
        hasActiveFilters: false,
        isLoading: false,
        isStageUpdatePending: false,
        loadMoreState: {},
        onClearFilters: jest.fn(),
        onDelete: jest.fn(),
        onDragEnd,
        onEdit,
        onLoadMore: jest.fn(),
        onRetry: jest.fn(),
      }),
    );
    const surface = screen.getByRole("button", {
      name: "Open Acme application",
    });
    surface.focus();

    await user.keyboard("{Enter}");
    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(onDragEnd).not.toHaveBeenCalled();

    fireEvent.keyDown(surface, { code: "Space", key: " " });
    await waitFor(() =>
      expect(surface.getAttribute("aria-pressed")).toBe("true"),
    );
    fireEvent.keyDown(document, {
      code: "ArrowRight",
      key: "ArrowRight",
    });
    fireEvent.keyDown(document, { code: "Space", key: " " });

    await waitFor(() => expect(onDragEnd).toHaveBeenCalledTimes(1));
    expect(onEdit).toHaveBeenCalledTimes(1);
  });

  it("keeps other cards openable while preventing duplicate deletion", async () => {
    const otherApplication: Application = {
      ...application,
      companyName: "Globex",
      id: "app-2",
    };
    const onDelete = jest.fn();
    const onEdit = jest.fn();
    const user = userEvent.setup();
    render(
      React.createElement(ApplicationsBoard, {
        board: {
          ...board,
          columns: board.columns.map((column) =>
            column.stage === "applied"
              ? {
                ...column,
                items: [application, otherApplication],
              }
              : column,
          ),
        },
        deletingApplicationId: application.id,
        errorMessage: null,
        hasActiveFilters: false,
        isLoading: false,
        isStageUpdatePending: false,
        loadMoreState: {},
        onClearFilters: jest.fn(),
        onDelete,
        onDragEnd: jest.fn(),
        onEdit,
        onLoadMore: jest.fn(),
        onRetry: jest.fn(),
      }),
    );

    expect(
      (
        screen.getByRole("button", {
          name: "Open Acme application",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(true);
    expect(
      (
        screen.getByRole("button", {
          name: "Delete Acme application",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(true);
    expect(
      (
        screen.getByRole("button", {
          name: "Open Globex application",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(false);
    expect(
      (
        screen.getByRole("button", {
          name: "Delete Globex application",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(true);
    expect(
      (screen.getByRole("button", { name: "Load more" }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);

    await user.click(
      screen.getByRole("button", { name: "Open Globex application" }),
    );
    expect(onEdit).toHaveBeenCalledWith(otherApplication);
    await user.click(
      screen.getByRole("button", { name: "Delete Globex application" }),
    );
    expect(onDelete).not.toHaveBeenCalled();
  });
});
