import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { LANDING_HEADLINE, LandingContent } from "./landing-content";

describe("LandingContent", () => {
  it("renders the landing content and CTA links", () => {
    const markup = renderToStaticMarkup(React.createElement(LandingContent));

    expect(markup).toContain(LANDING_HEADLINE);
    expect(markup).toContain('href="/register"');
    expect(markup).toContain('href="/login"');
    expect(markup).toContain("How it works");
    expect(markup).toContain("Save an opportunity");
    expect(markup).toContain("Keep every opportunity organized");
    expect(markup).toContain(
      "Track applications, interviews, follow-ups, and notes without",
    );
    expect(markup.match(/aria-hidden="true"/g)).toHaveLength(4);
  });
});
