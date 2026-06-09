import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { SettingsForm } from "./settings-form";

const renderSettingsForm = (overrides = {}) => {
  return renderToStaticMarkup(
    React.createElement(SettingsForm, {
      disabled: false,
      errorMessage: null,
      form: {
        followUpAfterApplyingDays: "10",
        upcomingInterviewDays: "14",
        followUpAfterInterviewDays: "4",
        targetRole: "Platform Engineer",
      },
      isSaving: false,
      successMessage: null,
      onFieldChange: jest.fn(),
      onSubmit: jest.fn(),
      ...overrides,
    }),
  );
};

describe("SettingsForm", () => {
  it("renders loaded settings", () => {
    const html = renderSettingsForm();

    expect(html).toContain("Follow up after applying");
    expect(html).toContain("Upcoming interviews window");
    expect(html).toContain("Follow up after interview");
    expect(html).toContain("Target role");
    expect(html).toContain("value=\"10\"");
    expect(html).toContain("value=\"14\"");
    expect(html).toContain("value=\"4\"");
    expect(html).toContain("value=\"Platform Engineer\"");
  });

  it("renders validation and success states", () => {
    const html = renderSettingsForm({
      errorMessage: "Target role must be at most 160 characters.",
      successMessage: "Settings saved.",
    });

    expect(html).toContain("Target role must be at most 160 characters.");
    expect(html).toContain("Settings saved.");
  });
});
