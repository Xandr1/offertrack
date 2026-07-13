import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { ShellLayout } from "./shell-layout";

describe("ShellLayout", () => {
  it("includes the Settings navigation link", () => {
    const html = renderToStaticMarkup(
      React.createElement(
        ShellLayout,
        { activeRoute: "/settings" },
        React.createElement("div", null, "Content"),
      ),
    );

    expect(html).toContain("Settings");
    expect(html).toContain("href=\"/settings\"");
    expect(html).toContain("aria-current=\"page\"");
    expect(html).toContain("data-testid=\"protected-page-shell\"");
    expect(html).toContain("data-protected-route=\"/settings\"");
    expect(html).toContain("md:grid-cols-[190px_minmax(0,1fr)]");
    expect(html).toContain("min-w-0");
    expect(html).toContain("min-h-screen w-full");
    expect(html).not.toContain("max-w-7xl");
  });
});
