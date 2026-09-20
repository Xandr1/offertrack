import { defineConfig, devices } from "@playwright/test";

const web = process.env.E2E_BASE_URL;
const core = process.env.E2E_API_URL;
if (!web || !core || new URL(web).protocol !== "https:" ||
    new URL(core).protocol !== "https:" || new URL(core).hostname !== "api." + new URL(web).hostname ||
    new URL(web).hostname.endsWith(".run.app")) {
  throw new Error("Supply the deployed same-site HTTPS Web and Core URLs.");
}
export default defineConfig({
  testDir: "./e2e",
  testMatch: "auth-session.spec.ts",
  workers: 1,
  fullyParallel: false,
  retries: 0,
  timeout: 45_000,
  reporter: [["line"]],
  outputDir: "test-results/auth-staging",
  use: { baseURL: web, trace: "off", video: "off", screenshot: "off" },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"], launchOptions: { args: ["--test-third-party-cookie-phaseout"] } } },
    { name: "firefox", use: { ...devices["Desktop Firefox"], launchOptions: { firefoxUserPrefs: { "network.cookie.cookieBehavior": 1 } } } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } },
  ],
});

