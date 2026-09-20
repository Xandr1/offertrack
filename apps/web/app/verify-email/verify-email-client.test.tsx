/** @jest-environment jsdom */
import React, { StrictMode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { VerifyEmailClient } from "./verify-email-client";
import { verifyEmail } from "@/lib/api";

jest.mock("@/lib/api", () => ({
  ...jest.requireActual("@/lib/api"), verifyEmail: jest.fn(),
}));
const verify = jest.mocked(verifyEmail);
const token = "a".repeat(43);

beforeEach(() => { verify.mockReset(); });
it("consumes a fragment once under React effect replay and removes it from the URL", async () => {
  history.replaceState(null, "", "/verify-email#token=" + token);
  verify.mockResolvedValue({ verified: true });
  render(<StrictMode><VerifyEmailClient /></StrictMode>);
  await screen.findByText("Email verified");
  expect(location.hash).toBe("");
  expect(verify).toHaveBeenCalledTimes(1);
  expect(verify).toHaveBeenCalledWith({ token });
  expect(document.body.textContent).not.toContain(token);
});
it("rejects old query-format links without submitting their tokens", async () => {
  history.replaceState(null, "", "/verify-email?token=" + token);
  render(<VerifyEmailClient />);
  await screen.findByText("Verification link missing");
  expect(verify).not.toHaveBeenCalled();
});
it("keeps the initial parsing state separate from a missing link", async () => {
  history.replaceState(null, "", "/verify-email");
  render(<VerifyEmailClient />);
  expect(screen.getByText("Verifying email")).toBeTruthy();
  await waitFor(() => expect(screen.getByText("Verification link missing")).toBeTruthy());
});
