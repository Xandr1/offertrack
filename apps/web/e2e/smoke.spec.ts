import { expect, Page, test } from "@playwright/test";

const E2E_PASSWORD = "E2e-Test-Password-123!";
const PROTECTED_SHELL_MARKER = '[data-testid="protected-page-shell"]';

const accounts = {
  navigation: { email: "navigation-user@e2e.invalid" },
  application: { email: "application-user@e2e.invalid" },
  applicationsGuard: { email: "applications-guard-user@e2e.invalid" },
  dashboardGuard: { email: "dashboard-guard-user@e2e.invalid" },
  settingsGuard: { email: "settings-guard-user@e2e.invalid" },
  logout: { email: "logout-user@e2e.invalid" },
} as const;

type E2eAccount = (typeof accounts)[keyof typeof accounts];

const login = async (page: Page, account: E2eAccount): Promise<void> => {
  await page.goto("/login");
  await page.getByLabel("Email").fill(account.email);
  await page.getByLabel("Password").fill(E2E_PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(
    page.getByRole("link", { name: "Dashboard" }),
  ).toHaveAttribute("aria-current", "page");
};

const installProtectedShellObserver = async (page: Page): Promise<void> => {
  await page.addInitScript(
    ({ marker }) => {
      const recordProtectedShell = (): void => {
        if (document.querySelector(marker)) {
          window.sessionStorage.setItem(
            "offertrack:e2e:protected-shell-observed",
            "true",
          );
        }
      };

      // The init script can run after the parser has created initial nodes, so
      // inspect the existing document before observing subsequent insertions.
      recordProtectedShell();

      const observer = new MutationObserver((mutations) => {
        for (const mutation of mutations) {
          if (
            mutation.type === "attributes" &&
            mutation.target instanceof Element &&
            mutation.target.matches(marker)
          ) {
            window.sessionStorage.setItem(
              "offertrack:e2e:protected-shell-observed",
              "true",
            );
            return;
          }

          for (const addedNode of mutation.addedNodes) {
            if (
              addedNode instanceof Element &&
              (addedNode.matches(marker) || addedNode.querySelector(marker))
            ) {
              window.sessionStorage.setItem(
                "offertrack:e2e:protected-shell-observed",
                "true",
              );
              return;
            }
          }
        }
      });
      observer.observe(document, {
        attributeFilter: ["data-testid"],
        attributes: true,
        childList: true,
        subtree: true,
      });

      document.addEventListener("DOMContentLoaded", recordProtectedShell, {
        once: true,
      });
    },
    {
      marker: PROTECTED_SHELL_MARKER,
    },
  );
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
  await login(page, accounts.navigation);

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

test("manual application persists after reload", async ({ page }) => {
  const company = "Isolated E2E Company";
  const position = "Isolated E2E Engineer";

  await login(page, accounts.application);
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

const protectedRouteScenarios = [
  { route: "/applications", account: accounts.applicationsGuard },
  { route: "/dashboard", account: accounts.dashboardGuard },
  { route: "/settings", account: accounts.settingsGuard },
] as const;

for (const { account, route } of protectedRouteScenarios) {
  test(`${route} never renders its protected shell after access expires`, async ({
    context,
    page,
  }) => {
    await login(page, account);
    await page.goto(route);
    await expect(page).toHaveURL(new RegExp(`${route}(?:\\?.*)?$`));
    await expect(page.locator(PROTECTED_SHELL_MARKER)).toBeVisible();

    await page.evaluate(() => {
      window.sessionStorage.removeItem(
        "offertrack:e2e:protected-shell-observed",
      );
    });
    await installProtectedShellObserver(page);
    await context.clearCookies({ name: "access_token" });
    await page.reload({ waitUntil: "domcontentloaded" });

    await expect(page).toHaveURL(/\/login$/);
    const protectedShellWasObserved = await page.evaluate(() => {
      return window.sessionStorage.getItem(
        "offertrack:e2e:protected-shell-observed",
      );
    });
    expect(protectedShellWasObserved).toBeNull();
    await expect(page.locator(PROTECTED_SHELL_MARKER)).toHaveCount(0);
  });
}

test("logout persists across direct protected navigation", async ({ page }) => {
  await login(page, accounts.logout);
  await page.getByRole("link", { name: "Settings" }).click();
  await page.getByRole("button", { name: "Sign out" }).click();

  await expect(page).toHaveURL(/\/login$/);
  await page.goto("/applications");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
});
