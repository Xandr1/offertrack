/** @jest-environment jsdom */

import React from "react";
import { render, screen } from "@testing-library/react";
import { initialApplicationFormState } from "../models/application-form-model";
import { ApplicationForm } from "./application-form";

describe("ApplicationForm accessibility", () => {
  it("uses valid accessible relationships and keeps every field visibly labelled", () => {
    const { container } = render(
      <ApplicationForm
        disabled={false}
        form={initialApplicationFormState}
        onFieldChange={jest.fn()}
      />,
    );

    for (const element of container.querySelectorAll("[aria-labelledby]")) {
      const labelledBy = element.getAttribute("aria-labelledby") ?? "";
      for (const id of labelledBy.split(/\s+/).filter(Boolean)) {
        expect(document.getElementById(id)).not.toBeNull();
      }
    }

    expect(
      container.querySelector(
        [
          '[aria-labelledby="application-role-heading"]',
          '[aria-labelledby="application-details-heading"]',
          '[aria-labelledby="application-notes-heading"]',
        ].join(","),
      ),
    ).toBeNull();

    const fieldLabels = [
      "Company",
      "Position",
      "Job URL",
      "Location",
      "Work mode",
      "Applied date",
      "Application stage",
      "Notes",
    ];
    for (const label of fieldLabels) {
      expect(screen.getByLabelText(label)).toBeTruthy();
    }

    const company = screen.getByLabelText("Company") as HTMLInputElement;
    const position = screen.getByLabelText("Position") as HTMLInputElement;
    expect(company.required).toBe(true);
    expect(position.required).toBe(true);
    expect(company.id).toBe("application-company");

    const ids = Array.from(container.querySelectorAll("[id]")).map(
      (element) => element.id,
    );
    expect(new Set(ids).size).toBe(ids.length);
  });
});
