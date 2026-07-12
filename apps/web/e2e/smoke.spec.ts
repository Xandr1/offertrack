import { expect, Page, test } from "@playwright/test";

const E2E_EMAIL = "e2e@example.com";
const E2E_PASSWORD = "E2e-Test-Password-123!";

const login = async (page: Page): Promise<void> => {
  await page.goto("/login");
  await page.getByLabel("Email").fill(E2E_EMAIL);
  await page.getByLabel("Password").fill(E2E_PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(
    page.getByRole("link", { name: "Dashboard" }),
  ).toHaveAttribute("aria-current", "page");
};

test("public landing page opens login without protected content", async ({
  page,
}) => {
  await page.goto("/");
  await expect(
    page.getByRole("heading", {
      name: "OfferTrack keeps your job search organized",
    }),
  ).toBeVisible();
  await expect(page.getByText("Applications to follow up")).toHaveCount(0);

  await page.getByRole("link", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
});

test("login permits protected navigation", async ({ page }) => {
  await login(page);

  await page.getByRole("link", { name: "Applications" }).click();
  await expect(page).toHaveURL(/\/applications(?:\?.*)?$/);
  await expect(
    page.getByRole("heading", { name: "Applications", exact: true }),
  ).toBeVisible();

  await page.getByRole("link", { name: "Settings" }).click();
  await expect(page).toHaveURL(/\/settings$/);
  await expect(
    page.getByRole("heading", { name: "Settings", exact: true }),
  ).toBeVisible();
});

test("manual application persists after reload", async ({ page }, testInfo) => {
  const uniqueSuffix = `${Date.now()}-${testInfo.workerIndex}`;
  const company = `E2E Company ${uniqueSuffix}`;
  const position = `E2E Engineer ${uniqueSuffix}`;

  await login(page);
  await page.getByRole("link", { name: "Applications" }).click();
  await page.getByRole("button", { name: "Add application" }).click();

  const dialog = page.getByRole("dialog", { name: "Create application" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Company").fill(company);
  await dialog.getByLabel("Position").fill(position);
  await dialog.getByRole("button", { name: "Create application" }).click();

  await expect(dialog).toBeHidden();
  await expect(page.getByRole("heading", { name: company })).toBeVisible();
  await expect(page.getByText(position, { exact: true })).toBeVisible();

  await page.reload();
  await expect(page.getByRole("heading", { name: company })).toBeVisible();
  await expect(page.getByText(position, { exact: true })).toBeVisible();
});

test("removed access cookie redirects without protected UI", async ({
  context,
  page,
}) => {
  await login(page);
  await page.getByRole("link", { name: "Applications" }).click();
  await expect(
    page.getByRole("heading", { name: "Applications", exact: true }),
  ).toBeVisible();

  await context.clearCookies({ name: "access_token" });
  await page.reload({ waitUntil: "domcontentloaded" });

  await expect(page).toHaveURL(/\/login$/);
  await expect(
    page.getByRole("heading", { name: "Applications", exact: true }),
  ).toHaveCount(0);
});

test("logout persists across direct protected navigation", async ({ page }) => {
  await login(page);
  await page.getByRole("link", { name: "Settings" }).click();
  await page.getByRole("button", { name: "Sign out" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await page.goto("/applications");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
});
