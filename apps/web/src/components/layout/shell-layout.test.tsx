/** @jest-environment jsdom */

import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ShellLayout } from "./shell-layout";
import {
  SIDEBAR_DOCUMENT_ATTRIBUTE,
  SIDEBAR_PREFERENCE_BOOTSTRAP_SCRIPT,
  parseStoredSidebarExpanded,
} from "./sidebar-preference";

const mockUsePathname = jest.fn(() => "/applications");
const mockUseSearchParams = jest.fn(() => new URLSearchParams());

jest.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
  useSearchParams: () => mockUseSearchParams(),
}));

const renderShell = () =>
  render(
    <ShellLayout>
      <div>Content</div>
    </ShellLayout>,
  );

describe("ShellLayout", () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute(SIDEBAR_DOCUMENT_ATTRIBUTE);
    mockUsePathname.mockReturnValue("/applications");
    mockUseSearchParams.mockReturnValue(new URLSearchParams());
  });

  it("derives the active navigation item from the current pathname", () => {
    const view = renderShell();

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
        "data-protected-route",
      ),
    ).toBe("/applications");

    mockUsePathname.mockReturnValue("/settings/profile");
    view.rerender(
      <ShellLayout>
        <div>Content</div>
      </ShellLayout>,
    );
    expect(
      screen
        .getByRole("link", { name: "Settings" })
        .getAttribute("aria-current"),
    ).toBe("page");
  });

  it("retains Applications filters across non-sidebar protected navigation", () => {
    mockUseSearchParams.mockReturnValue(
      new URLSearchParams(
        "stage=applied&search=platform&id=application-to-edit",
      ),
    );
    const view = renderShell();

    expect(
      screen.getByRole("link", { name: "Applications" }).getAttribute("href"),
    ).toBe("/applications?stage=applied&search=platform");

    mockUsePathname.mockReturnValue("/dashboard");
    mockUseSearchParams.mockReturnValue(new URLSearchParams());
    view.rerender(
      <ShellLayout>
        <div>Content</div>
      </ShellLayout>,
    );

    expect(
      screen.getByRole("link", { name: "Applications" }).getAttribute("href"),
    ).toBe("/applications?stage=applied&search=platform");
  });

  it("renders an accessible, contained, minimal sidebar toggle", () => {
    const { container } = renderShell();

    const toggle = screen.getByRole("button", { name: "Collapse sidebar" });
    const toggleRow = container.querySelector("[data-sidebar-toggle-row]");
    expect(toggle.getAttribute("aria-expanded")).toBe("true");
    expect(toggle.className).toContain("h-8");
    expect(toggle.className).toContain("w-8");
    expect(toggle.className).not.toContain("hover:bg-");
    expect(toggle.className).not.toContain("hover:text-");
    expect(toggle.className).toContain("focus-visible:ring-2");
    expect(toggle.className).toContain("focus-visible:ring-inset");
    expect(toggle.className).toContain("motion-reduce:transition-none");
    expect(toggle.className).not.toContain("absolute");
    expect(toggle.className).not.toContain("-left-");
    expect(toggle.className).not.toContain("shadow");
    expect(toggle.className).not.toContain("border");
    expect(toggleRow?.className).toContain("border-y");
    expect(toggleRow?.className).toContain("border-zinc-200");
    expect(toggleRow?.className).toContain("-left-3");
    expect(toggleRow?.className).toContain("-right-3");
    expect(toggleRow?.className).toContain("top-11");
    expect(toggleRow?.className).toContain("h-10");
    expect(toggleRow?.className).toContain("justify-end");
    expect(toggleRow?.className).toContain("pr-[17px]");
    expect(toggleRow?.className).toContain("duration-300");
    expect(toggleRow?.className).toContain("motion-reduce:transition-none");
    expect(
      screen.getByTestId("protected-page-shell").getAttribute(
        "data-sidebar-state",
      ),
    ).toBe("expanded");
  });

  it("animates collapsed labels without layout gaps or hidden display", async () => {
    const user = userEvent.setup();
    const { container } = renderShell();

    await user.click(
      screen.getByRole("button", { name: "Collapse sidebar" }),
    );

    const toggle = screen.getByRole("button", { name: "Expand sidebar" });
    const toggleRow = container.querySelector("[data-sidebar-toggle-row]");
    expect(toggle.getAttribute("aria-expanded")).toBe("false");
    expect(toggleRow?.className).toContain("border-transparent");
    expect(toggleRow?.className).not.toContain("border-zinc-200");
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

    const header = container.querySelector("[data-sidebar-header]");
    expect(header?.className).toContain("md:h-20");
    expect(header?.className).not.toContain("flex-col");
    const brand = container.querySelector("[data-sidebar-brand]");
    expect(brand?.className).toContain("md:pl-[5px]");
    expect(brand?.className).not.toContain("gap-");

    for (const link of container.querySelectorAll("[data-sidebar-nav-link]")) {
      expect(link.className).toContain("h-10");
      expect(link.className).toContain("gap-0");
      expect(link.className).toContain("duration-300");
      expect(link.className).toContain("motion-reduce:transition-none");
    }

    for (const label of container.querySelectorAll("[data-sidebar-label]")) {
      expect(label.className).toContain("max-w-28");
      expect(label.className).toContain("duration-300");
      expect(label.className).toContain("motion-reduce:transition-none");
      expect(label.className.split(" ")).not.toContain("hidden");
      expect(label.className.split(" ")).not.toContain("md:hidden");
    }
    expect(container.innerHTML).toContain("overflow-x-hidden");
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
