import {
  validateWebBuildEnvironment,
  validateWebEnvironment,
} from "./web-environment";

describe("validateWebEnvironment", () => {
  it("uses the local API default during local development", () => {
    expect(validateWebEnvironment({})).toEqual({
      appEnv: "local",
      apiUrl: "http://localhost:8080",
    });
  });

  it("requires explicit build environment values", () => {
    expect(() =>
      validateWebBuildEnvironment({}),
    ).toThrow("APP_ENV must be set");

    expect(() =>
      validateWebBuildEnvironment({ APP_ENV: "test" }),
    ).toThrow("NEXT_PUBLIC_API_URL must be set");
  });

  it.each(["staging", "production"] as const)(
    "requires a non-loopback HTTPS URL for %s",
    (appEnv) => {
      expect(() =>
        validateWebEnvironment({
          APP_ENV: appEnv,
          NEXT_PUBLIC_API_URL: "http://api.example.com",
        }),
      ).toThrow("must use HTTPS");

      expect(() =>
        validateWebEnvironment({
          APP_ENV: appEnv,
          NEXT_PUBLIC_API_URL: "https://127.0.0.1:8080",
        }),
      ).toThrow("must not use localhost or a loopback address");
    },
  );

  it.each([
    "https://[::]:8443",
    "https://[0:0:0:0:0:0:0:0]:8443",
    "https://[0:0:0:0:0:0:0:1]:8443",
    "https://[::ffff:127.0.0.1]:8443",
    "https://[::ffff:7f00:1]:8443",
    "https://[::ffff:192.0.2.1]:8443",
    "https://localhost.:8443",
  ])("rejects protected loopback form %s", (apiUrl) => {
    expect(() =>
      validateWebBuildEnvironment({
        APP_ENV: "production",
        NEXT_PUBLIC_API_URL: apiUrl,
      }),
    ).toThrow("must not use localhost or a loopback address");
  });

  it.each([
    "https://@api.example.com",
    "https://user@api.example.com",
    "https://user:password@api.example.com",
  ])("rejects URL credentials in %s", (apiUrl) => {
    expect(() =>
      validateWebBuildEnvironment({
        APP_ENV: "e2e",
        NEXT_PUBLIC_API_URL: apiUrl,
      }),
    ).toThrow("must not include credentials");
  });

  it.each([
    "https://api.example.com?",
    "https://api.example.com?debug=true",
    "https://api.example.com#",
    "https://api.example.com#fragment",
  ])("rejects URL query or fragment syntax in %s", (apiUrl) => {
    expect(() =>
      validateWebBuildEnvironment({
        APP_ENV: "e2e",
        NEXT_PUBLIC_API_URL: apiUrl,
      }),
    ).toThrow("must not include a query or fragment");
  });

  it("accepts loopback HTTP for test and E2E builds", () => {
    expect(
      validateWebEnvironment(
        {
          APP_ENV: "e2e",
          NEXT_PUBLIC_API_URL: "http://127.0.0.1:18080/",
        },
        { requireExplicit: true },
      ),
    ).toEqual({
      appEnv: "e2e",
      apiUrl: "http://127.0.0.1:18080",
    });
  });

  it("accepts an explicit protected API URL", () => {
    expect(
      validateWebEnvironment(
        {
          APP_ENV: "production",
          NEXT_PUBLIC_API_URL: "https://api.offertrack.example",
        },
        { requireExplicit: true },
      ),
    ).toEqual({
      appEnv: "production",
      apiUrl: "https://api.offertrack.example",
    });
  });

  it("does not let protected build validation fall back to localhost", () => {
    expect(() =>
      validateWebBuildEnvironment({ APP_ENV: "production" }),
    ).toThrow("NEXT_PUBLIC_API_URL must be set");
  });
});
