import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { LoginEmailVerificationMessage } from "./page";

describe("LoginEmailVerificationMessage", () => {
  it("renders verification and resend messaging", () => {
    const markup = renderToStaticMarkup(
      React.createElement(LoginEmailVerificationMessage, {
        isResending: false,
        resendError: null,
        resendMessage: "Another verification email has been sent.",
        onResend: () => undefined,
      }),
    );

    expect(markup).toContain("Please verify your email before signing in.");
    expect(markup).toContain("Resend verification email");
    expect(markup).toContain("Another verification email has been sent.");
  });
});
