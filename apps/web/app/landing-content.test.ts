import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { LANDING_HEADLINE, LandingContent } from "./landing-content";

describe("LandingContent", () => {
  it("renders the headline and CTA links", () => {
    const markup = renderToStaticMarkup(React.createElement(LandingContent));

    expect(markup).toContain(LANDING_HEADLINE);
    expect(markup).toContain('href="/register"');
    expect(markup).toContain('href="/login"');
  });
});
