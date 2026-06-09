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
  });
});
