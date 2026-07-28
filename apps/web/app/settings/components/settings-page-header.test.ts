import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { SettingsPageHeader } from "./settings-page-header";

describe("SettingsPageHeader", () => {
  it("renders account email and sign out action in the header", () => {
    const html = renderToStaticMarkup(
      React.createElement(SettingsPageHeader, {
        email: "ollek@example.com",
        onSignOut: jest.fn(),
      }),
    );

    expect(html).toContain("Settings");
    expect(html).toContain("Configure your dashboard timing and target role.");
    expect(html).toContain("Account");
    expect(html).toContain("ollek@example.com");
    expect(html).toContain("Sign out");
  });
});
