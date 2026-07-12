/** @jest-environment jsdom */

import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ResetPasswordForm } from "./reset-password-client";

describe("ResetPasswordForm", () => {
  afterEach(() => {
    delete (global as { fetch?: typeof fetch }).fetch;
    jest.restoreAllMocks();
  });

  it("validates matching passwords before submitting", async () => {
    const fetchSpy = jest.fn();
    global.fetch = fetchSpy as unknown as typeof fetch;
    const user = userEvent.setup();

    render(React.createElement(ResetPasswordForm, { token: "reset-token" }));

    await user.type(screen.getByLabelText("New password"), "Password1");
    await user.type(screen.getByLabelText("Confirm password"), "Password2");
    await user.click(screen.getByRole("button", { name: "Reset password" }));

    expect(screen.getByText("Passwords do not match.")).toBeTruthy();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("submits a valid reset token and renders the success state", async () => {
    const fetchSpy = mockOkFetch();
    const user = userEvent.setup();

    render(React.createElement(ResetPasswordForm, { token: "reset-token" }));

    await user.type(screen.getByLabelText("New password"), "NewPassword1");
    await user.type(screen.getByLabelText("Confirm password"), "NewPassword1");
    await user.click(screen.getByRole("button", { name: "Reset password" }));

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(2));
    expect(fetchSpy.mock.calls[0][0]).toBe("http://localhost:8080/auth/csrf");
    expect(fetchSpy.mock.calls[1][0]).toBe(
      "http://localhost:8080/auth/password/reset",
    );
    expect((fetchSpy.mock.calls[1][1] as RequestInit).body).toBe(
      JSON.stringify({ token: "reset-token", newPassword: "NewPassword1" }),
    );
    expect(screen.getByText("Password reset")).toBeTruthy();
    expect(screen.getByRole("link", { name: "Sign in" })).toBeTruthy();
  });
});

const mockOkFetch = () => {
  const fetchMock = jest
    .fn()
    .mockResolvedValueOnce({
      ok: true,
      status: 200,
      text: async () =>
        JSON.stringify({ token: "masked-token", headerName: "X-XSRF-TOKEN" }),
    } as Response)
    .mockResolvedValueOnce({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ ok: true }),
    } as Response);

  global.fetch = fetchMock as unknown as typeof fetch;
  return fetchMock;
};
