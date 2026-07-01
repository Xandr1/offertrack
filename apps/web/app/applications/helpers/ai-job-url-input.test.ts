import { normalizeAiJobUrlInput } from "./ai-job-url-input";

describe("normalizeAiJobUrlInput", () => {
  it("requires a non-empty URL", () => {
    expect(normalizeAiJobUrlInput("   ")).toEqual({
      jobUrl: null,
      error: "Enter a job URL.",
    });
  });

  it("rejects URLs longer than 2048 characters", () => {
    expect(normalizeAiJobUrlInput("a".repeat(2049))).toEqual({
      jobUrl: null,
      error: "Job URL must be 2048 characters or fewer.",
    });
  });

  it("adds https to host-like values", () => {
    expect(normalizeAiJobUrlInput("example.com/jobs/123")).toEqual({
      jobUrl: "https://example.com/jobs/123",
      error: null,
    });
  });

  it("keeps http and https URLs", () => {
    expect(normalizeAiJobUrlInput("http://example.com/jobs/123")).toEqual({
      jobUrl: "http://example.com/jobs/123",
      error: null,
    });
    expect(normalizeAiJobUrlInput("https://example.com/jobs/123")).toEqual({
      jobUrl: "https://example.com/jobs/123",
      error: null,
    });
  });

  it("rejects unsupported schemes", () => {
    expect(normalizeAiJobUrlInput("ftp://example.com/jobs/123")).toEqual({
      jobUrl: null,
      error: "Enter a valid http or https job URL.",
    });
  });

  it("rejects unsupported values and invalid URLs", () => {
    expect(normalizeAiJobUrlInput("not a url")).toEqual({
      jobUrl: null,
      error: "Enter a valid http or https job URL.",
    });
    expect(normalizeAiJobUrlInput("https://")).toEqual({
      jobUrl: null,
      error: "Enter a valid http or https job URL.",
    });
  });
});
