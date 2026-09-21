import { expect, test, type Page } from "@playwright/test";

const api = process.env.E2E_API_URL ?? "http://127.0.0.1:18080";
const staging = new URL(api).protocol === "https:";
const email = staging ? process.env.E2E_STAGING_EMAIL : "navigation-user@e2e.invalid";
const password = staging ? process.env.E2E_STAGING_PASSWORD : "E2e-Test-Password-123!";
test.use({ trace: "off", video: "off", screenshot: "off" });

async function login(page: Page) {
  await page.goto("/login");
  let status: number;
  try {
    // Keep credentials out of Playwright action descriptions and failure artifacts.
    status = await page.evaluate(async values => {
      const csrfResponse = await fetch(values.api + "/auth/csrf", { credentials: "include" });
      const csrf = await csrfResponse.json();
      const response = await fetch(values.api + "/auth/login", {
        method: "POST", credentials: "include",
        headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token },
        body: JSON.stringify({ email: values.email, password: values.password }),
      });
      return response.status;
    }, { api, email, password });
  } catch { throw new Error("Authentication setup could not complete."); }
  expect(status).toBe(200);
  await page.goto("/dashboard");
  await expect(page.getByRole("heading", { name: "Dashboard", exact: true })).toBeVisible();
}

test("exact CORS, explicit authentication errors, and selector-only candidate routing", async ({ request, baseURL }) => {
  const allowed = await request.get(api + "/auth/csrf", { headers: { Origin: baseURL! } });
  expect(allowed.status()).toBe(200);
  expect(allowed.headers()["access-control-allow-origin"]).toBe(baseURL);
  expect(allowed.headers()["access-control-allow-credentials"]).toBe("true");
  const denied = await request.get(api + "/auth/csrf", { headers: { Origin: "https://untrusted.example" } });
  expect(denied.status()).toBe(403);
  const selectors: Record<string, string>[] = [{}, { "X-OfferTrack-Route": "candidate" }];
  for (const headers of selectors) {
    const response = await request.get(api + "/api/me", { headers });
    expect(response.status()).toBe(401);
    expect((await response.json()).code).toBe("AUTHENTICATION_REQUIRED");
  }
  const dependency = await request.get(api + "/actuator/health/dependencies", {
    headers: { "X-OfferTrack-Route": "candidate" },
  });
  // No candidate tag after promotion may return 404, but it must never grant access.
  expect([403, 404, 503]).toContain(dependency.status());
});

test("refresh-cookie-only CSRF, cross-tab refresh, and logout propagation", async ({ page, context }) => {
  test.skip(!email || !password, "Requires an authorized disposable staging account.");
  await login(page);
  const cookies = (await context.cookies(api + "/auth/refresh")).map(({ name, domain, path, secure, httpOnly, sameSite }) =>
    ({ name, domain, path, secure, httpOnly, sameSite }));
  for (const [name, path] of [["access_token", "/"], ["refresh_token", "/auth"]]) {
    expect(cookies.find(cookie => cookie.name === name)).toMatchObject({
      name, path, domain: new URL(api).hostname, secure: staging, httpOnly: true, sameSite: "Lax",
    });
  }
  const second = await context.newPage();
  await second.goto("/dashboard");
  await expect(second.getByRole("heading", { name: "Dashboard", exact: true })).toBeVisible();
  await context.clearCookies({ name: "access_token" });
  const missingCsrf = await context.request.post(api + "/auth/refresh");
  expect(missingCsrf.status()).toBe(403);
  let rotations = 0;
  context.on("request", request => { if (new URL(request.url()).pathname === "/auth/refresh") rotations++; });
  await Promise.all([page.reload(), second.reload()]);
  await expect(page.getByRole("heading", { name: "Dashboard", exact: true })).toBeVisible();
  await expect(second.getByRole("heading", { name: "Dashboard", exact: true })).toBeVisible();
  expect(rotations).toBe(1);
  await page.goto("/settings");
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(second).toHaveURL(/\/login$/);
  await expect(second.locator('[data-testid="protected-page-shell"]')).toHaveCount(0);
});

test("logout-all invalidates another browser session and missing refresh leads to login", async ({ page, browser, baseURL }) => {
  test.skip(!email || !password, "Requires an authorized disposable staging account.");
  await login(page);
  const other = await browser.newContext({ baseURL });
  try {
    const otherPage = await other.newPage();
    await login(otherPage);
    await page.goto("/settings");
    await page.getByRole("button", { name: "Sign out all devices" }).click();
    await expect(page).toHaveURL(/\/login$/);
    await otherPage.reload();
    await expect(otherPage).toHaveURL(/\/login$/);
    await login(otherPage);
    await other.clearCookies({ name: "access_token" });
    await other.clearCookies({ name: "refresh_token" });
    await otherPage.reload();
    await expect(otherPage).toHaveURL(/\/login$/);
  } finally { await other.close(); }
});

test("forged forwarding chains cannot evade the browser IP login limit", async ({ request, baseURL }) => {
  test.skip(process.env.E2E_RATE_LIMIT_PROBE !== "true", "Opt-in probe consumes the source IP login window.");
  const csrfResponse = await request.get(api + "/auth/csrf", { headers: { Origin: baseURL! } });
  const csrf = await csrfResponse.json();
  let limited = false;
  for (let index = 0; index < 21; index++) {
    const response = await request.post(api + "/auth/login", {
      headers: {
        Origin: baseURL!, [csrf.headerName]: csrf.token,
        "X-Forwarded-For": "192.0.2." + (index + 1),
        Forwarded: "for=198.51.100." + (index + 1),
        "X-Forwarded-Host": "untrusted.example", "X-Forwarded-Proto": "http",
        "X-Forwarded-Port": "81", "X-Forwarded-Prefix": "/forged",
      },
      data: { email: "forwarding-probe-" + crypto.randomUUID() + "@example.invalid", password: "Invalid1!" },
    });
    if (response.status() === 429) { limited = true; break; }
    expect(response.status()).toBe(401);
  }
  expect(limited).toBe(true);
});
