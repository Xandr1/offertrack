/** @jest-environment jsdom */

import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  ShellLayout,
} from "./shell-layout";
import {
  SIDEBAR_DOCUMENT_ATTRIBUTE,
  SIDEBAR_PREFERENCE_BOOTSTRAP_SCRIPT,
  parseStoredSidebarExpanded,
} from "./sidebar-preference";

const renderShell = () =>
  render(
    <ShellLayout activeRoute="/applications">
      <div>Content</div>
    </ShellLayout>,
  );

describe("ShellLayout", () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute(SIDEBAR_DOCUMENT_ATTRIBUTE);
  });

  it("renders expanded navigation with accessible names and active state", () => {
    renderShell();

    expect(screen.getByRole("link", { name: "Dashboard" })).toBeTruthy();
    expect(
      screen.getByRole("link", { name: "Applications" }).getAttribute(
        "aria-current",
      ),
    ).toBe("page");
    expect(screen.getByRole("link", { name: "Settings" })).toBeTruthy();
    const toggle = screen.getByRole("button", { name: "Collapse sidebar" });
    expect(toggle.getAttribute("aria-expanded")).toBe("true");
    expect(
      screen.getByTestId("protected-page-shell").getAttribute(
        "data-sidebar-state",
      ),
    ).toBe("expanded");
  });

  it("collapses, exposes icon tooltips, and persists the preference", async () => {
    const user = userEvent.setup();
    renderShell();

    await user.click(
      screen.getByRole("button", { name: "Collapse sidebar" }),
    );

    const toggle = screen.getByRole("button", { name: "Expand sidebar" });
    expect(toggle.getAttribute("aria-expanded")).toBe("false");
    expect(
      window.localStorage.getItem("offertrack.sidebar.expanded"),
    ).toBe("false");
    expect(
      document.documentElement.getAttribute(SIDEBAR_DOCUMENT_ATTRIBUTE),
    ).toBe("collapsed");
    expect(
      screen.getByTestId("protected-page-shell").getAttribute(
        "data-sidebar-state",
      ),
    ).toBe("collapsed");
    expect(screen.getAllByRole("tooltip")).toHaveLength(3);
    const grid = screen.getByTestId("protected-page-shell").firstElementChild;
    expect(grid?.className).toContain("md:grid-cols-[66px_minmax(0,1fr)]");
    expect(grid?.className).toContain("duration-300");
    expect(grid?.className).toContain("motion-reduce:transition-none");
  });

  it("restores a valid collapsed preference and defaults invalid values", async () => {
    window.localStorage.setItem("offertrack.sidebar.expanded", "false");
    const view = renderShell();

    expect(
      screen.getByRole("button", { name: "Expand sidebar" }),
    ).toBeTruthy();
    view.unmount();

    window.localStorage.setItem("offertrack.sidebar.expanded", "invalid");
    renderShell();
    await screen.findByRole("button", { name: "Collapse sidebar" });

    expect(parseStoredSidebarExpanded("true")).toBe(true);
    expect(parseStoredSidebarExpanded("false")).toBe(false);
    expect(parseStoredSidebarExpanded("invalid")).toBe(true);
    expect(parseStoredSidebarExpanded(null)).toBe(true);
  });

  it("restores the stored state without a delayed timer", () => {
    window.localStorage.setItem("offertrack.sidebar.expanded", "false");
    const { container } = renderShell();

    expect(
      screen.getByTestId("protected-page-shell").getAttribute(
        "data-sidebar-state",
      ),
    ).toBe("collapsed");
    expect(container.innerHTML).toContain("min-w-0");
    expect(container.innerHTML).toContain("min-h-screen w-full");
  });

  it("provides a pre-paint bootstrap for the persisted preference", () => {
    expect(SIDEBAR_PREFERENCE_BOOTSTRAP_SCRIPT).toContain(
      'getItem("offertrack.sidebar.expanded")',
    );
    expect(SIDEBAR_PREFERENCE_BOOTSTRAP_SCRIPT).toContain(
      `"${SIDEBAR_DOCUMENT_ATTRIBUTE}"`,
    );
  });
});
