/** @jest-environment jsdom */

import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  ForgotPasswordForm,
  PASSWORD_RESET_REQUEST_MESSAGE,
} from "./page";

describe("ForgotPasswordForm", () => {
  afterEach(() => {
    delete (global as { fetch?: typeof fetch }).fetch;
    jest.restoreAllMocks();
  });

  it("submits the email and renders the generic success state", async () => {
    const fetchSpy = mockOkFetch();
    const user = userEvent.setup();

    render(React.createElement(ForgotPasswordForm));

    await user.type(screen.getByLabelText("Email"), "person@example.com");
    await user.click(screen.getByRole("button", { name: "Send reset link" }));

    await waitFor(() => expect(fetchSpy).toHaveBeenCalledTimes(1));
    expect(fetchSpy.mock.calls[0][0]).toBe(
      "http://localhost:8080/auth/password/forgot",
    );
    expect((fetchSpy.mock.calls[0][1] as RequestInit).body).toBe(
      JSON.stringify({ email: "person@example.com" }),
    );
    expect(screen.getByText(PASSWORD_RESET_REQUEST_MESSAGE)).toBeTruthy();
  });
});

const mockOkFetch = () => {
  const fetchMock = jest.fn().mockResolvedValueOnce({
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ ok: true }),
  } as Response);

  global.fetch = fetchMock as unknown as typeof fetch;
  return fetchMock;
};
