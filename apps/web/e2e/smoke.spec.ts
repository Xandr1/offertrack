import { expect, type Locator, type Page, test } from "@playwright/test";

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

const expectNoPageOverflow = async (page: Page): Promise<void> => {
  await expect
    .poll(async () =>
      page.evaluate(
        () =>
          document.documentElement.scrollWidth <=
          document.documentElement.clientWidth + 1,
      ),
    )
    .toBe(true);
};

const navigateToApplications = async (page: Page): Promise<void> => {
  const applicationsLink = page.getByRole("link", { name: "Applications" });

  await Promise.all([
    page.waitForURL(/\/applications(?:\?.*)?$/),
    applicationsLink.click(),
  ]);
  await expect(
    page.getByRole("heading", { name: "Applications", exact: true }),
  ).toBeVisible();
};

type VisibleBox = NonNullable<Awaited<ReturnType<Locator["boundingBox"]>>>;

const getVisibleBox = async (
  locator: Locator,
  name: string,
): Promise<VisibleBox> => {
  await expect(locator, `${name} should be visible`).toBeVisible();
  const box = await locator.boundingBox();
  expect(box, `${name} should have a bounding box`).not.toBeNull();
  return box!;
};

type ToolbarLayout = "compact-wide" | "intermediate" | "narrow" | "wide";

const expectApplicationsToolbarLayout = async (
  page: Page,
  layout: ToolbarLayout,
  includeClearFilters: boolean,
): Promise<void> => {
  const toolbar = page.getByTestId("applications-toolbar");
  const shellPanel = page
    .getByTestId("protected-page-shell")
    .locator(":scope > div > section");
  const controls = [
    {
      locator: toolbar.getByRole("group", { name: "Applications view" }),
      name: "Applications view",
    },
    {
      locator: toolbar.getByRole("combobox", { name: "Stage" }),
      name: "Stage",
    },
    {
      locator: toolbar.getByRole("textbox", {
        name: "Search applications",
      }),
      name: "Search",
    },
    {
      locator: toolbar.getByRole("combobox", { name: "Sort" }),
      name: "Sort",
    },
    {
      locator: toolbar.getByRole("combobox", { name: "Direction" }),
      name: "Direction",
    },
  ];

  if (includeClearFilters) {
    controls.push({
      locator: toolbar.getByRole("button", { name: "Clear filters" }),
      name: "Clear filters",
    });
  }

  const toolbarBox = await getVisibleBox(toolbar, "Applications toolbar");
  const shellPanelBox = await getVisibleBox(shellPanel, "Protected shell panel");
  const boxes = new Map<string, VisibleBox>();

  for (const control of controls) {
    boxes.set(
      control.name,
      await getVisibleBox(control.locator, `${control.name} control`),
    );
  }

  const tolerance = 1;

  expect(
    toolbarBox.x,
    "Toolbar should remain inside the shell panel on the left",
  ).toBeGreaterThanOrEqual(shellPanelBox.x - tolerance);
  expect(
    toolbarBox.x + toolbarBox.width,
    "Toolbar should remain inside the shell panel on the right",
  ).toBeLessThanOrEqual(shellPanelBox.x + shellPanelBox.width + tolerance);

  for (const [name, box] of boxes) {
    expect(
      box.x,
      `${name} should remain inside the toolbar on the left`,
    ).toBeGreaterThanOrEqual(toolbarBox.x - tolerance);
    expect(
      box.y,
      `${name} should remain inside the toolbar at the top`,
    ).toBeGreaterThanOrEqual(toolbarBox.y - tolerance);
    expect(
      box.x + box.width,
      `${name} should remain inside the shell panel on the right`,
    ).toBeLessThanOrEqual(shellPanelBox.x + shellPanelBox.width + tolerance);
    expect(
      box.y + box.height,
      `${name} should remain inside the toolbar at the bottom`,
    ).toBeLessThanOrEqual(toolbarBox.y + toolbarBox.height + tolerance);
  }

  const controlEntries = [...boxes.entries()];
  for (let index = 0; index < controlEntries.length; index += 1) {
    const [firstName, firstBox] = controlEntries[index];

    for (
      let comparisonIndex = index + 1;
      comparisonIndex < controlEntries.length;
      comparisonIndex += 1
    ) {
      const [secondName, secondBox] = controlEntries[comparisonIndex];
      const overlapWidth =
        Math.min(
          firstBox.x + firstBox.width,
          secondBox.x + secondBox.width,
        ) - Math.max(firstBox.x, secondBox.x);
      const overlapHeight =
        Math.min(
          firstBox.y + firstBox.height,
          secondBox.y + secondBox.height,
        ) - Math.max(firstBox.y, secondBox.y);

      expect(
        overlapWidth <= tolerance || overlapHeight <= tolerance,
        `${firstName} and ${secondName} controls should not overlap`,
      ).toBe(true);
    }
  }

  const viewBox = boxes.get("Applications view")!;
  const stageBox = boxes.get("Stage")!;
  const searchBox = boxes.get("Search")!;
  const sortBox = boxes.get("Sort")!;
  const directionBox = boxes.get("Direction")!;

  if (layout === "wide" || layout === "compact-wide") {
    const rowCenters = [
      viewBox,
      stageBox,
      searchBox,
      sortBox,
      directionBox,
      ...(includeClearFilters ? [boxes.get("Clear filters")!] : []),
    ].map((box) => box.y + box.height / 2);
    expect(Math.max(...rowCenters) - Math.min(...rowCenters)).toBeLessThanOrEqual(
      tolerance,
    );

    if (layout === "compact-wide") {
      expect(stageBox.width).toBeLessThanOrEqual(140 + tolerance);
      expect(sortBox.width).toBeLessThanOrEqual(150 + tolerance);
      expect(directionBox.width).toBeLessThanOrEqual(110 + tolerance);
    } else {
      expect(stageBox.width).toBeGreaterThanOrEqual(170 - tolerance);
      expect(sortBox.width).toBeGreaterThanOrEqual(180 - tolerance);
      expect(directionBox.width).toBeGreaterThanOrEqual(130 - tolerance);
    }
  } else if (layout === "intermediate") {
    const firstRowBottom = Math.max(
      viewBox.y + viewBox.height,
      stageBox.y + stageBox.height,
      sortBox.y + sortBox.height,
      directionBox.y + directionBox.height,
      includeClearFilters
        ? boxes.get("Clear filters")!.y + boxes.get("Clear filters")!.height
        : 0,
    );
    expect(searchBox.y).toBeGreaterThan(firstRowBottom + tolerance);
  } else {
    const firstRowBottom = Math.max(
      viewBox.y + viewBox.height,
      stageBox.y + stageBox.height,
    );
    expect(searchBox.y).toBeGreaterThan(firstRowBottom + tolerance);
    expect(Math.min(sortBox.y, directionBox.y)).toBeGreaterThan(
      searchBox.y + searchBox.height + tolerance,
    );
  }
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

  await navigateToApplications(page);

  await page.getByRole("link", { name: "Settings" }).click();
  await expect(page).toHaveURL(/\/settings$/);
  await expect(
    page.getByRole("heading", { name: "Settings", exact: true }),
  ).toBeVisible();
});

test("protected navigation preserves the shell and Applications state", async ({
  page,
}) => {
  let authMeRequestCount = 0;
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.endsWith("/auth/me")) {
      authMeRequestCount += 1;
    }
  });

  await login(page, accounts.navigation);
  await navigateToApplications(page);

  const shell = page.getByTestId("protected-page-shell");
  await shell.evaluate((element) => {
    element.setAttribute("data-e2e-persistent-shell", "mounted");
  });

  await page
    .getByRole("combobox", { name: "Stage" })
    .selectOption("applied");
  await page.getByRole("button", { name: "Board" }).click();
  await expect(page.getByRole("button", { name: "Board" })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await expect(
    page.getByRole("link", { name: "Applications" }),
  ).toHaveAttribute("href", /[?&]stage=applied(?:&|$)/);
  const authenticatedRequestCount = authMeRequestCount;

  const dashboardLink = page.getByRole("link", { name: "Dashboard" });
  await Promise.all([
    page.waitForURL(/\/dashboard$/),
    dashboardLink.click(),
  ]);
  await expect(
    page.getByRole("heading", { name: "Dashboard", exact: true }),
  ).toBeVisible();
  await expect(shell).toHaveAttribute(
    "data-e2e-persistent-shell",
    "mounted",
  );

  await navigateToApplications(page);
  await expect(
    page.getByRole("combobox", { name: "Stage" }),
  ).toHaveValue("applied");
  await expect(page.getByRole("button", { name: "Board" })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await expect(shell).toHaveAttribute(
    "data-e2e-persistent-shell",
    "mounted",
  );
  expect(authMeRequestCount).toBe(authenticatedRequestCount);
});

test("manual application persists after reload", async ({ page }) => {
  const company = "Isolated E2E Company";
  const position = "Isolated E2E Engineer";

  await login(page, accounts.application);
  await navigateToApplications(page);
  await page.getByRole("button", { name: "Add application" }).click();

  const dialog = page.getByRole("dialog", { name: "Create application" });
  await expect(dialog).toBeVisible();
  await dialog.getByRole("radio", { name: "Manual" }).click();
  await dialog.getByLabel("Company").fill(company);
  await dialog.getByLabel("Position").fill(position);
  await dialog.getByRole("button", { name: "Add interview" }).click();
  await expect(
    dialog.getByRole("combobox", { name: "Interview type" }),
  ).toHaveValue("other");
  await expect(
    dialog.getByRole("combobox", { name: "Interview status" }),
  ).toHaveValue("initial");
  await dialog.getByRole("button", { name: "Create application" }).click();

  await expect(dialog).toBeHidden();
  await expect(page.getByRole("heading", { name: company })).toBeVisible();
  await expect(page.getByText(position, { exact: true })).toBeVisible();

  await page.reload();
  await expect(page.getByRole("heading", { name: company })).toBeVisible();
  await expect(page.getByText(position, { exact: true })).toBeVisible();
});

const responsiveViewports = [
  {
    activeFilters: false,
    height: 900,
    label: "1600x900",
    layout: "wide",
    width: 1600,
  },
  {
    activeFilters: true,
    height: 900,
    label: "1440x900",
    layout: "wide",
    width: 1440,
  },
  {
    activeFilters: true,
    height: 768,
    label: "1366x768",
    layout: "wide",
    width: 1366,
  },
  {
    activeFilters: true,
    height: 900,
    label: "1280x900",
    layout: "wide",
    width: 1280,
  },
  {
    activeFilters: false,
    height: 900,
    label: "1279x900",
    layout: "compact-wide",
    width: 1279,
  },
  {
    activeFilters: false,
    height: 768,
    label: "1024x768",
    layout: "compact-wide",
    width: 1024,
  },
  {
    activeFilters: false,
    height: 768,
    label: "1023x768",
    layout: "intermediate",
    width: 1023,
  },
  {
    activeFilters: false,
    height: 900,
    label: "768x900",
    layout: "intermediate",
    width: 768,
  },
] as const;

for (const viewport of responsiveViewports) {
  test(`Applications remains usable without page overflow at ${viewport.label}`, async ({
    page,
  }) => {
    await page.setViewportSize({
      height: viewport.height,
      width: viewport.width,
    });
    await login(page, accounts.navigation);
    await navigateToApplications(page);

    if (viewport.activeFilters) {
      await page
        .getByRole("combobox", { name: "Stage" })
        .selectOption("applied");
      await expect(
        page.getByRole("button", { name: "Clear filters" }),
      ).toBeVisible();
    }

    const search = page.getByRole("textbox", { name: "Search applications" });
    await expect(search).toBeVisible();
    const searchBox = await search.boundingBox();
    expect(searchBox?.width ?? 0).toBeGreaterThan(180);
    await expect(page.getByRole("combobox", { name: "Stage" })).toBeVisible();
    await expect(page.getByRole("combobox", { name: "Sort" })).toBeVisible();
    await expect(
      page.getByRole("combobox", { name: "Direction" }),
    ).toBeVisible();
    await expectApplicationsToolbarLayout(
      page,
      viewport.layout,
      viewport.activeFilters,
    );
    await expectNoPageOverflow(page);

    await page.getByRole("button", { name: "Board" }).click();
    await expect(page.getByRole("heading", { name: "Initial" })).toBeVisible();
    await expectNoPageOverflow(page);

    await page.getByRole("button", { name: "Add application" }).click();
    const dialog = page.getByRole("dialog", { name: "Create application" });
    await expect(dialog).toBeVisible();
    await dialog.getByRole("radio", { name: "Manual" }).click();
    await expect(dialog.getByLabel("Company")).toBeFocused();
    const dialogBox = await dialog.boundingBox();
    expect(dialogBox).not.toBeNull();
    expect(dialogBox!.x).toBeGreaterThanOrEqual(0);
    expect(dialogBox!.y).toBeGreaterThanOrEqual(0);
    expect(dialogBox!.width).toBeLessThanOrEqual(viewport.width);
    expect(dialogBox!.height).toBeLessThanOrEqual(viewport.height);
    await expect(
      dialog.getByRole("button", { name: "Create application" }),
    ).toBeVisible();
    await dialog.getByRole("button", { name: "Cancel" }).click();
    await expect(dialog).toBeHidden();
  });
}

test("collapsed sidebar expands content and persists across navigation and reload", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1024, height: 768 });
  await login(page, accounts.navigation);

  const shell = page.getByTestId("protected-page-shell");
  const content = shell.locator(":scope > div > section");
  const expandedBox = await content.boundingBox();
  await page.getByRole("button", { name: "Collapse sidebar" }).click();

  await expect(shell).toHaveAttribute("data-sidebar-state", "collapsed");
  await expect(
    page.getByRole("button", { name: "Expand sidebar" }),
  ).toHaveAttribute("aria-expanded", "false");
  await expect
    .poll(async () => (await content.boundingBox())?.width ?? 0)
    .toBeGreaterThan(expandedBox?.width ?? 0);

  const applicationsLink = page.getByRole("link", { name: "Applications" });
  await applicationsLink.hover();
  await expect(page.getByRole("tooltip", { name: "Applications" })).toBeVisible();
  await Promise.all([
    page.waitForURL(/\/applications(?:\?.*)?$/),
    applicationsLink.click(),
  ]);
  await expect(
    page.getByRole("heading", { name: "Applications", exact: true }),
  ).toBeVisible();
  await expect(shell).toHaveAttribute("data-sidebar-state", "collapsed");

  await page.route("**/*", async (route) => {
    if (route.request().resourceType() === "script") {
      await route.abort();
      return;
    }

    await route.continue();
  });
  await page.reload({ waitUntil: "domcontentloaded" });
  await expect(page.locator("html")).toHaveAttribute(
    "data-offertrack-sidebar",
    "collapsed",
  );
  await page.unroute("**/*");

  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Applications", exact: true }),
  ).toBeVisible();
  await expect(shell).toHaveAttribute("data-sidebar-state", "collapsed");
  await expect
    .poll(
      async () =>
        (await page.locator("[data-sidebar-grid] > aside").boundingBox())
          ?.width ?? Number.POSITIVE_INFINITY,
    )
    .toBeLessThanOrEqual(68);
  await expect(page.getByRole("link", { name: "Dashboard" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Settings" })).toBeVisible();
  await expectNoPageOverflow(page);
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
