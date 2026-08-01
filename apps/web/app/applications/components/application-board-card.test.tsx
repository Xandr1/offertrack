/** @jest-environment jsdom */

import React from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useDraggable } from "@dnd-kit/core";
import type { Application } from "@/lib/api";
import { ApplicationBoardCard } from "./application-board-card";

jest.mock("@dnd-kit/core", () => ({
  ...jest.requireActual("@dnd-kit/core"),
  useDraggable: jest.fn(),
}));

const mockedUseDraggable = useDraggable as jest.MockedFunction<
  typeof useDraggable
>;
const dragKeyDown = jest.fn();
const dragPointerDown = jest.fn();
let isDragging = false;

const application: Application = {
  appliedAt: null,
  companyName: "Acme",
  createdAt: "2026-01-01T00:00:00Z",
  followedUpAt: null,
  id: "app-1",
  jobUrl: null,
  lastInterview: null,
  location: "Warsaw",
  nextInterview: null,
  notes: null,
  positionTitle: "Engineer",
  stage: "applied",
  updatedAt: "2026-01-01T00:00:00Z",
  workMode: "hybrid",
};

const renderCard = ({
  dragDisabled = false,
  onDelete = jest.fn(),
  onEdit = jest.fn(),
}: {
  dragDisabled?: boolean;
  onDelete?: jest.Mock;
  onEdit?: jest.Mock;
} = {}) => {
  const view = render(
    <ApplicationBoardCard
      application={application}
      dragDisabled={dragDisabled}
      onDelete={onDelete}
      onEdit={onEdit}
    />,
  );

  return { ...view, onDelete, onEdit };
};

describe("ApplicationBoardCard", () => {
  beforeEach(() => {
    isDragging = false;
    dragKeyDown.mockReset();
    dragPointerDown.mockReset();
    mockedUseDraggable.mockImplementation(
      () =>
        ({
          attributes: {
            "aria-describedby": "drag-instructions",
            "aria-disabled": false,
            "aria-pressed": undefined,
            "aria-roledescription": "draggable",
            role: "button",
            tabIndex: 0,
          },
          isDragging,
          listeners: {
            onKeyDown: dragKeyDown,
            onPointerDown: dragPointerDown,
          },
          setActivatorNodeRef: jest.fn(),
          setNodeRef: jest.fn(),
          transform: null,
        }) as never,
    );
  });

  it("uses the non-delete card surface for opening and drag activation", async () => {
    const user = userEvent.setup();
    const { onDelete, onEdit } = renderCard();
    const surface = screen.getByRole("button", {
      name: "Open Acme application",
    });
    const deleteButton = screen.getByRole("button", {
      name: "Delete Acme application",
    });

    expect(surface.className).toContain("cursor-pointer");
    expect(surface.getAttribute("aria-roledescription")).toBe("draggable");
    expect(surface.closest("article")?.querySelectorAll("button")).toHaveLength(
      2,
    );
    expect(screen.queryByRole("button", { name: "Edit" })).toBeNull();

    await user.click(surface);
    expect(onEdit).toHaveBeenCalledWith(application);

    dragPointerDown.mockClear();
    fireEvent.pointerDown(deleteButton);
    fireEvent.click(deleteButton);
    expect(dragPointerDown).not.toHaveBeenCalled();
    expect(onDelete).toHaveBeenCalledWith(application);
    expect(onEdit).toHaveBeenCalledTimes(1);
  });

  it("opens with Enter and reserves Space for keyboard dragging", async () => {
    const user = userEvent.setup();
    const { onEdit } = renderCard();
    const surface = screen.getByRole("button", {
      name: "Open Acme application",
    });

    await user.type(surface, "{Enter}");

    expect(onEdit).toHaveBeenCalledWith(application);
    expect(dragKeyDown).not.toHaveBeenCalled();

    fireEvent.keyDown(surface, { code: "Space", key: " " });

    expect(dragKeyDown).toHaveBeenCalledTimes(1);
  });

  it("suppresses the click emitted when an actual drag finishes", () => {
    jest.useFakeTimers();
    const onEdit = jest.fn();
    const view = renderCard({ onEdit });

    isDragging = true;
    view.rerender(
      <ApplicationBoardCard
        application={application}
        dragDisabled={false}
        onDelete={view.onDelete}
        onEdit={onEdit}
      />,
    );
    isDragging = false;
    view.rerender(
      <ApplicationBoardCard
        application={application}
        dragDisabled={false}
        onDelete={view.onDelete}
        onEdit={onEdit}
      />,
    );

    fireEvent.click(
      screen.getByRole("button", { name: "Open Acme application" }),
    );
    expect(onEdit).not.toHaveBeenCalled();

    act(() => {
      jest.runOnlyPendingTimers();
    });
    fireEvent.click(
      screen.getByRole("button", { name: "Open Acme application" }),
    );
    expect(onEdit).toHaveBeenCalledTimes(1);
    jest.useRealTimers();
  });

  it("disables opening, deleting, and dragging while movement is pending", () => {
    renderCard({ dragDisabled: true });

    const surface = screen.getByRole("button", {
      name: "Open Acme application",
    }) as HTMLButtonElement;
    const deleteButton = screen.getByRole("button", {
      name: "Delete Acme application",
    }) as HTMLButtonElement;

    expect(surface.disabled).toBe(true);
    expect(surface.className).toContain("cursor-not-allowed");
    expect(deleteButton.disabled).toBe(true);
    expect(mockedUseDraggable).toHaveBeenCalledWith({
      disabled: true,
      id: "app-1",
    });
  });
});
