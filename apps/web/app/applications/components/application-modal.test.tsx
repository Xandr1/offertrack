/** @jest-environment jsdom */

import React, { useState } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApplicationCreateMethodSwitch } from "./application-create-method-switch";
import { ApplicationModal } from "./application-modal";

const ModalHarness = ({ closeDisabled = false }: { closeDisabled?: boolean }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [method, setMethod] = useState<"ai" | "manual">("ai");

  return (
    <div data-testid="background">
      <button onClick={() => setIsOpen(true)}>Open application modal</button>
      <ApplicationModal
        initialFocusSelector="#initial-field"
        isCloseDisabled={closeDisabled}
        isOpen={isOpen}
        title="Accessible application"
        onClose={() => setIsOpen(false)}
      >
        <label htmlFor="initial-field">Initial field</label>
        <input id="initial-field" />
        <button type="button">Last action</button>
        <ApplicationCreateMethodSwitch
          method={method}
          onChange={setMethod}
        />
        <button tabIndex={-1} type="button">
          Programmatic action
        </button>
        <button disabled type="button">
          Disabled action
        </button>
        <button hidden type="button">
          Hidden action
        </button>
        <div inert>
          <button type="button">Inert action</button>
        </div>
        <button style={{ visibility: "hidden" }} type="button">
          Invisible action
        </button>
      </ApplicationModal>
    </div>
  );
};

describe("ApplicationModal accessibility", () => {
  it("traps focus, makes the background inert, closes on Escape, and restores focus", async () => {
    const user = userEvent.setup();
    render(<ModalHarness />);
    const opener = screen.getByRole("button", {
      name: "Open application modal",
    });

    await user.click(opener);
    const initialField = screen.getByLabelText("Initial field");
    await waitFor(() => expect(document.activeElement).toBe(initialField));
    expect(
      screen.getByTestId("background").parentElement?.getAttribute("inert"),
    ).toBe("");
    expect(document.body.style.overflow).toBe("hidden");

    await user.tab();
    expect(document.activeElement).toBe(
      screen.getByRole("button", { name: "Last action" }),
    );
    await user.tab();
    const aiOption = screen.getByRole("radio", { name: "AI" });
    const manualOption = screen.getByRole("radio", { name: "Manual" });
    expect(document.activeElement).toBe(aiOption);
    expect(manualOption.tabIndex).toBe(-1);
    await user.tab();
    expect(document.activeElement).toBe(
      screen.getByRole("button", { name: "Close modal" }),
    );
    await user.tab({ shift: true });
    expect(document.activeElement).toBe(aiOption);

    await user.keyboard("{Escape}");
    await waitFor(() =>
      expect(
        screen.queryByRole("dialog", { name: "Accessible application" }),
      ).toBeNull(),
    );
    expect(document.activeElement).toBe(opener);
    expect(document.body.style.overflow).toBe("");
  });

  it("does not close with Escape while closing is disabled", async () => {
    const user = userEvent.setup();
    render(<ModalHarness closeDisabled />);

    await user.click(
      screen.getByRole("button", { name: "Open application modal" }),
    );
    await screen.findByRole("dialog", { name: "Accessible application" });
    await user.keyboard("{Escape}");

    expect(
      screen.getByRole("dialog", { name: "Accessible application" }),
    ).toBeTruthy();
    expect(
      (screen.getByRole("button", {
        name: "Close modal",
      }) as HTMLButtonElement).disabled,
    ).toBe(true);
  });
});
