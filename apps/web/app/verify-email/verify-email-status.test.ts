import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { VerifyEmailStatus } from "./verify-email-client";

describe("VerifyEmailStatus", () => {
  it("renders success state", () => {
    const markup = renderToStaticMarkup(
      React.createElement(VerifyEmailStatus, { status: "success" }),
    );

    expect(markup).toContain("Email verified");
    expect(markup).toContain("You can now sign in.");
  });

  it("renders error state", () => {
    const markup = renderToStaticMarkup(
      React.createElement(VerifyEmailStatus, {
        errorMessage: "Verification link is invalid or expired.",
        status: "error",
      }),
    );

    expect(markup).toContain("Could not verify email");
    expect(markup).toContain("Verification link is invalid or expired.");
  });
});
