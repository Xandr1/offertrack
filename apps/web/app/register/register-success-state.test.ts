import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { RegisterSuccessState } from "./page";

describe("RegisterSuccessState", () => {
  it("renders the check-email state", () => {
    const markup = renderToStaticMarkup(
      React.createElement(RegisterSuccessState, {
        email: "new-user@example.com",
      }),
    );

    expect(markup).toContain("Check your email");
    expect(markup).toContain("new-user@example.com");
    expect(markup).toContain("Sign in");
  });
});
