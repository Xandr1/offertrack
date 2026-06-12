import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { LoginEmailVerificationMessage, LoginGoogleAction } from "./login-client";

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

describe("LoginGoogleAction", () => {
  it("renders a normal link to the backend Google OAuth start endpoint", () => {
    const markup = renderToStaticMarkup(React.createElement(LoginGoogleAction));

    expect(markup).toContain("Continue with Google");
    expect(markup).toContain(
      'href="http://localhost:8080/auth/oauth2/google/start"',
    );
    expect(markup).toContain('viewBox="0 0 18 18"');
    expect(markup).toContain('fill="#4285F4"');
    expect(markup).toContain("or sign in with email");
  });
});
