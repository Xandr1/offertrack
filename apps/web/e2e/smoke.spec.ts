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

type ToolbarLayout = "intermediate" | "narrow" | "wide";

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
  const viewport = page.viewportSize();
  expect(viewport, "The page should have a fixed test viewport").not.toBeNull();
  const boxes = new Map<string, VisibleBox>();

  for (const control of controls) {
    boxes.set(
      control.name,
      await getVisibleBox(control.locator, `${control.name} control`),
    );
  }

  const tolerance = 1;
  const shellPanelContentLeft = await shellPanel.evaluate((element) => {
    const box = element.getBoundingClientRect();
    const styles = window.getComputedStyle(element);
    return box.left + Number.parseFloat(styles.paddingLeft);
  });
  const toolbarContentLeft = await toolbar.evaluate((element) => {
    const box = element.getBoundingClientRect();
    const styles = window.getComputedStyle(element);
    return (
      box.left +
      Number.parseFloat(styles.borderLeftWidth) +
      Number.parseFloat(styles.paddingLeft)
    );
  });

  expect(
    toolbarBox.x,
    "Toolbar should remain inside the shell panel on the left",
  ).toBeGreaterThanOrEqual(shellPanelBox.x - tolerance);
  expect(
    toolbarBox.x + toolbarBox.width,
    "Toolbar should remain inside the shell panel on the right",
  ).toBeLessThanOrEqual(shellPanelBox.x + shellPanelBox.width + tolerance);
  expect(
    toolbarBox.y,
    "Toolbar should remain inside the shell panel at the top",
  ).toBeGreaterThanOrEqual(shellPanelBox.y - tolerance);
  expect(
    toolbarBox.y + toolbarBox.height,
    "Toolbar should remain inside the shell panel at the bottom",
  ).toBeLessThanOrEqual(shellPanelBox.y + shellPanelBox.height + tolerance);
  expect(
    Math.abs(toolbarBox.x - shellPanelContentLeft),
    "Toolbar should be left-aligned with the shell panel content",
  ).toBeLessThanOrEqual(tolerance);
  expect(toolbarBox.x).toBeGreaterThanOrEqual(-tolerance);
  expect(toolbarBox.y).toBeGreaterThanOrEqual(-tolerance);
  expect(toolbarBox.x + toolbarBox.width).toBeLessThanOrEqual(
    viewport!.width + tolerance,
  );
  expect(toolbarBox.y + toolbarBox.height).toBeLessThanOrEqual(
    viewport!.height + tolerance,
  );

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
      `${name} should remain inside the toolbar on the right`,
    ).toBeLessThanOrEqual(toolbarBox.x + toolbarBox.width + tolerance);
    expect(
      box.y + box.height,
      `${name} should remain inside the toolbar at the bottom`,
    ).toBeLessThanOrEqual(toolbarBox.y + toolbarBox.height + tolerance);
    expect(
      box.x,
      `${name} should remain inside the shell panel on the left`,
    ).toBeGreaterThanOrEqual(shellPanelBox.x - tolerance);
    expect(
      box.y,
      `${name} should remain inside the shell panel at the top`,
    ).toBeGreaterThanOrEqual(shellPanelBox.y - tolerance);
    expect(
      box.x + box.width,
      `${name} should remain inside the shell panel on the right`,
    ).toBeLessThanOrEqual(shellPanelBox.x + shellPanelBox.width + tolerance);
    expect(
      box.y + box.height,
      `${name} should remain inside the shell panel at the bottom`,
    ).toBeLessThanOrEqual(shellPanelBox.y + shellPanelBox.height + tolerance);
    expect(box.x, `${name} should remain inside the viewport`).toBeGreaterThanOrEqual(
      -tolerance,
    );
    expect(box.y, `${name} should remain inside the viewport`).toBeGreaterThanOrEqual(
      -tolerance,
    );
    expect(
      box.x + box.width,
      `${name} should remain inside the viewport on the right`,
    ).toBeLessThanOrEqual(viewport!.width + tolerance);
    expect(
      box.y + box.height,
      `${name} should remain inside the viewport at the bottom`,
    ).toBeLessThanOrEqual(viewport!.height + tolerance);
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
  const clearFiltersBox = includeClearFilters
    ? boxes.get("Clear filters")!
    : null;

  expect(
    Math.abs(viewBox.x - toolbarContentLeft),
    "The first toolbar control should be left-aligned",
  ).toBeLessThanOrEqual(tolerance);

  if (layout === "wide") {
    const rowCenters = [
      viewBox,
      stageBox,
      searchBox,
      sortBox,
      directionBox,
      ...(clearFiltersBox ? [clearFiltersBox] : []),
    ].map((box) => box.y + box.height / 2);
    expect(Math.max(...rowCenters) - Math.min(...rowCenters)).toBeLessThanOrEqual(
      tolerance,
    );
  } else if (layout === "intermediate") {
    const firstRow = [
      viewBox,
      stageBox,
      sortBox,
      directionBox,
      ...(clearFiltersBox ? [clearFiltersBox] : []),
    ];
    const firstRowCenters = firstRow.map((box) => box.y + box.height / 2);
    expect(
      Math.max(...firstRowCenters) - Math.min(...firstRowCenters),
    ).toBeLessThanOrEqual(tolerance);
    const firstRowBottom = Math.max(
      ...firstRow.map((box) => box.y + box.height),
    );
    expect(searchBox.y).toBeGreaterThan(firstRowBottom + tolerance);
    expect(
      Math.abs(searchBox.x - toolbarContentLeft),
      "Intermediate search should start at the toolbar content edge",
    ).toBeLessThanOrEqual(tolerance);
  } else {
    const firstRowCenters = [viewBox, stageBox].map(
      (box) => box.y + box.height / 2,
    );
    expect(
      Math.max(...firstRowCenters) - Math.min(...firstRowCenters),
    ).toBeLessThanOrEqual(tolerance);
    const firstRowBottom = Math.max(
      ...[viewBox, stageBox].map((box) => box.y + box.height),
    );
    expect(searchBox.y).toBeGreaterThan(firstRowBottom + tolerance);
    expect(
      Math.abs(searchBox.x - toolbarContentLeft),
      "Narrow search should start at the toolbar content edge",
    ).toBeLessThanOrEqual(tolerance);
    const lastRow = [
      sortBox,
      directionBox,
      ...(clearFiltersBox ? [clearFiltersBox] : []),
    ];
    const lastRowCenters = lastRow.map((box) => box.y + box.height / 2);
    expect(
      Math.max(...lastRowCenters) - Math.min(...lastRowCenters),
    ).toBeLessThanOrEqual(tolerance);
    expect(Math.min(...lastRow.map((box) => box.y))).toBeGreaterThan(
      searchBox.y + searchBox.height + tolerance,
    );
  }
};

const expectInterviewRoundsLayout = async (
  page: Page,
  dialog: Locator,
  viewportLabel: string,
): Promise<void> => {
  const tolerance = 1;
  const section = dialog.getByTestId("interview-rounds");
  const rows = section.getByTestId("interview-row");
  await expect(rows).toHaveCount(3);
  await section.scrollIntoViewIfNeeded();

  const viewport = page.viewportSize();
  expect(viewport, `${viewportLabel} should have a fixed viewport`).not.toBeNull();
  const dialogBox = await getVisibleBox(dialog, `${viewportLabel} dialog`);
  const sectionBox = await getVisibleBox(
    section,
    `${viewportLabel} interview rounds`,
  );

  expect(sectionBox.x).toBeGreaterThanOrEqual(dialogBox.x - tolerance);
  expect(sectionBox.x + sectionBox.width).toBeLessThanOrEqual(
    dialogBox.x + dialogBox.width + tolerance,
  );
  expect(sectionBox.y).toBeGreaterThanOrEqual(dialogBox.y - tolerance);
  expect(sectionBox.y + sectionBox.height).toBeLessThanOrEqual(
    dialogBox.y + dialogBox.height + tolerance,
  );
  expect(sectionBox.x).toBeGreaterThanOrEqual(-tolerance);
  expect(sectionBox.x + sectionBox.width).toBeLessThanOrEqual(
    viewport!.width + tolerance,
  );
  expect(sectionBox.y).toBeGreaterThanOrEqual(-tolerance);
  expect(sectionBox.y + sectionBox.height).toBeLessThanOrEqual(
    viewport!.height + tolerance,
  );
  expect(
    await section.evaluate(
      (element) => element.scrollWidth <= element.clientWidth + 1,
    ),
    `${viewportLabel} interview rounds should not overflow horizontally`,
  ).toBe(true);

  const rowBoxes: VisibleBox[] = [];
  for (let rowIndex = 0; rowIndex < 3; rowIndex += 1) {
    const row = rows.nth(rowIndex);
    const rowBox = await getVisibleBox(
      row,
      `${viewportLabel} interview row ${rowIndex + 1}`,
    );
    rowBoxes.push(rowBox);

    expect(rowBox.x).toBeGreaterThanOrEqual(sectionBox.x - tolerance);
    expect(rowBox.x + rowBox.width).toBeLessThanOrEqual(
      sectionBox.x + sectionBox.width + tolerance,
    );
    expect(
      await row.evaluate(
        (element) => element.scrollWidth <= element.clientWidth + 1,
      ),
      `${viewportLabel} interview row ${rowIndex + 1} should not overflow`,
    ).toBe(true);

    const controls = [
      {
        locator: row.getByRole("combobox", { name: "Interview type" }),
        name: "type",
      },
      {
        locator: row.getByRole("combobox", { name: "Interview status" }),
        name: "status",
      },
      {
        locator: row.getByLabel("Scheduled date and time"),
        name: "date",
      },
      {
        locator: row.getByRole("button", { name: "Delete interview row" }),
        name: "delete",
      },
    ];
    const controlBoxes: Array<{ box: VisibleBox; name: string }> = [];

    for (const control of controls) {
      const box = await getVisibleBox(
        control.locator,
        `${viewportLabel} row ${rowIndex + 1} ${control.name}`,
      );
      controlBoxes.push({ box, name: control.name });
      expect(box.x).toBeGreaterThanOrEqual(rowBox.x - tolerance);
      expect(box.y).toBeGreaterThanOrEqual(rowBox.y - tolerance);
      expect(box.x + box.width).toBeLessThanOrEqual(
        rowBox.x + rowBox.width + tolerance,
      );
      expect(box.y + box.height).toBeLessThanOrEqual(
        rowBox.y + rowBox.height + tolerance,
      );
    }

    for (const { box, name } of controlBoxes.slice(0, 3)) {
      expect(
        box.width,
        `${viewportLabel} row ${rowIndex + 1} ${name} should remain usable`,
      ).toBeGreaterThanOrEqual(160);
    }

    const rowCenters = controlBoxes.map(({ box }) => box.y + box.height / 2);
    expect(
      Math.max(...rowCenters) - Math.min(...rowCenters),
      `${viewportLabel} row ${rowIndex + 1} controls should share one row`,
    ).toBeLessThanOrEqual(tolerance);

    for (let index = 0; index < controlBoxes.length; index += 1) {
      const first = controlBoxes[index];
      for (
        let comparisonIndex = index + 1;
        comparisonIndex < controlBoxes.length;
        comparisonIndex += 1
      ) {
        const second = controlBoxes[comparisonIndex];
        const overlapWidth =
          Math.min(
            first.box.x + first.box.width,
            second.box.x + second.box.width,
          ) - Math.max(first.box.x, second.box.x);
        const overlapHeight =
          Math.min(
            first.box.y + first.box.height,
            second.box.y + second.box.height,
          ) - Math.max(first.box.y, second.box.y);

        expect(
          overlapWidth <= tolerance || overlapHeight <= tolerance,
          `${viewportLabel} row ${rowIndex + 1} ${first.name} and ${second.name} should not overlap`,
        ).toBe(true);
      }
    }
  }

  for (let rowIndex = 1; rowIndex < rowBoxes.length; rowIndex += 1) {
    expect(rowBoxes[rowIndex].y).toBeGreaterThan(
      rowBoxes[rowIndex - 1].y + rowBoxes[rowIndex - 1].height + tolerance,
    );
  }

  await expectNoPageOverflow(page);
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

test("edit application interview rounds remain aligned at desktop widths", async ({
  page,
}) => {
  const company = "Responsive Interview Layout Company";
  const pageErrors: string[] = [];
  page.on("pageerror", (error) => {
    pageErrors.push(error.message);
  });

  await page.setViewportSize({ height: 900, width: 1440 });
  await login(page, accounts.application);
  await navigateToApplications(page);
  await page.getByRole("button", { name: "Add application" }).click();

  const createDialog = page.getByRole("dialog", {
    name: "Create application",
  });
  await expect(createDialog).toBeVisible();
  await createDialog.getByRole("radio", { name: "Manual" }).click();
  await createDialog.getByLabel("Company").fill(company);
  await createDialog.getByLabel("Position").fill("Layout Verification Engineer");
  await createDialog.getByRole("button", { name: "Add interview" }).click();
  await createDialog
    .getByRole("button", { name: "Create application" })
    .click();

  await expect(createDialog).toBeHidden();
  const applicationCard = page
    .getByRole("heading", { name: company })
    .locator("xpath=ancestor::article");
  await expect(applicationCard).toBeVisible();
  await applicationCard
    .getByRole("button", { name: "Edit application" })
    .click();

  const editDialog = page.getByRole("dialog", { name: "Edit application" });
  await expect(editDialog).toBeVisible();
  const interviewRows = editDialog.getByTestId("interview-row");
  await expect(interviewRows).toHaveCount(1);
  await editDialog.getByRole("button", { name: "Add interview" }).click();
  await editDialog.getByRole("button", { name: "Add interview" }).click();
  await expect(interviewRows).toHaveCount(3);

  await interviewRows
    .nth(0)
    .getByRole("combobox", { name: "Interview type" })
    .selectOption("recruiter");
  await interviewRows
    .nth(0)
    .getByRole("combobox", { name: "Interview status" })
    .selectOption("passed");
  await interviewRows
    .nth(2)
    .getByRole("combobox", { name: "Interview type" })
    .selectOption("hiring_manager");
  await interviewRows
    .nth(2)
    .getByRole("combobox", { name: "Interview status" })
    .selectOption("scheduled");

  const modalViewports = [
    { height: 2160, label: "3840x2160", width: 3840 },
    { height: 900, label: "1440x900", width: 1440 },
    { height: 900, label: "1280x900", width: 1280 },
    { height: 768, label: "1024x768", width: 1024 },
  ] as const;

  for (const viewport of modalViewports) {
    await page.setViewportSize({
      height: viewport.height,
      width: viewport.width,
    });
    await expectInterviewRoundsLayout(page, editDialog, viewport.label);
  }

  expect(pageErrors, "The application modal should not raise page errors").toEqual(
    [],
  );
});

const responsiveViewports = [
  {
    activeFilters: true,
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
    activeFilters: true,
    height: 900,
    label: "1279x900",
    layout: "intermediate",
    width: 1279,
  },
  {
    activeFilters: true,
    height: 768,
    label: "1024x768",
    layout: "intermediate",
    width: 1024,
  },
  {
    activeFilters: true,
    height: 768,
    label: "1023x768",
    layout: "narrow",
    width: 1023,
  },
  {
    activeFilters: false,
    height: 900,
    label: "768x900",
    layout: "narrow",
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
    if (viewport.activeFilters) {
      await page.getByRole("button", { name: "Clear filters" }).click();
      await expect(
        page.getByRole("button", { name: "Board" }),
      ).toHaveAttribute("aria-pressed", "true");
      await expect(page.getByRole("heading", { name: "Initial" })).toBeVisible();
      await expectNoPageOverflow(page);
    }

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
  const sidebar = shell.locator("[data-sidebar-grid] > aside");
  const sidebarGrid = shell.locator("[data-sidebar-grid]");
  const sidebarLabel = shell.locator("[data-sidebar-label]").first();
  const collapseToggle = page.getByRole("button", {
    name: "Collapse sidebar",
  });
  await expect(sidebarGrid).toHaveCSS("transition-duration", "0.3s");
  await expect(sidebarLabel).toHaveCSS("transition-duration", "0.3s");
  await expect(collapseToggle).toHaveCSS("transition-duration", "0.3s");

  const expandedSidebarBox = await getVisibleBox(sidebar, "Expanded sidebar");
  const expandedLogoBox = await getVisibleBox(
    page.getByAltText("OfferTrack logo"),
    "Expanded sidebar logo",
  );
  const expandedToggleBox = await getVisibleBox(
    collapseToggle,
    "Collapse sidebar toggle",
  );
  expect(expandedToggleBox.x).toBeGreaterThanOrEqual(
    expandedSidebarBox.x - 1,
  );
  expect(expandedToggleBox.x + expandedToggleBox.width).toBeLessThanOrEqual(
    expandedSidebarBox.x + expandedSidebarBox.width + 1,
  );
  expect(expandedToggleBox.x).toBeGreaterThan(
    expandedLogoBox.x + expandedLogoBox.width + 1,
  );
  expect(
    expandedSidebarBox.x +
      expandedSidebarBox.width -
      (expandedToggleBox.x + expandedToggleBox.width),
  ).toBeLessThanOrEqual(18);
  expect(expandedToggleBox.y).toBeGreaterThan(
    expandedLogoBox.y + expandedLogoBox.height + 1,
  );

  await page.evaluate(() => {
    const monitor = { active: true, maxOverflow: 0 };
    (
      window as typeof window & {
        __offerTrackOverflowMonitor?: typeof monitor;
      }
    ).__offerTrackOverflowMonitor = monitor;

    const sampleOverflow = () => {
      monitor.maxOverflow = Math.max(
        monitor.maxOverflow,
        document.documentElement.scrollWidth -
          document.documentElement.clientWidth,
      );
      if (monitor.active) {
        window.requestAnimationFrame(sampleOverflow);
      }
    };

    window.requestAnimationFrame(sampleOverflow);
  });

  const expandedBox = await content.boundingBox();
  await collapseToggle.click();

  await expect(shell).toHaveAttribute("data-sidebar-state", "collapsed");
  const expandToggle = page.getByRole("button", { name: "Expand sidebar" });
  await expect(expandToggle).toHaveAttribute("aria-expanded", "false");
  await expect
    .poll(async () => (await content.boundingBox())?.width ?? 0)
    .toBeGreaterThan(expandedBox?.width ?? 0);
  expect(await sidebarLabel.evaluate((element) => getComputedStyle(element).display))
    .not.toBe("none");
  await expect(sidebarLabel).toHaveCSS("max-width", "0px");
  await expect(sidebarLabel).toHaveCSS("opacity", "0");

  const collapsedSidebarBox = await getVisibleBox(
    sidebar,
    "Collapsed sidebar",
  );
  const collapsedToggleBox = await getVisibleBox(
    expandToggle,
    "Expand sidebar toggle",
  );
  expect(collapsedToggleBox.x).toBeGreaterThanOrEqual(
    collapsedSidebarBox.x - 1,
  );
  expect(collapsedToggleBox.x + collapsedToggleBox.width).toBeLessThanOrEqual(
    collapsedSidebarBox.x + collapsedSidebarBox.width + 1,
  );

  const logoBox = await getVisibleBox(
    page.getByAltText("OfferTrack logo"),
    "Collapsed sidebar logo",
  );
  expect(
    Math.abs(
      logoBox.x +
        logoBox.width / 2 -
        (collapsedSidebarBox.x + collapsedSidebarBox.width / 2),
    ),
  ).toBeLessThanOrEqual(1);
  expect(
    Math.abs(
      collapsedToggleBox.x +
        collapsedToggleBox.width / 2 -
        (logoBox.x + logoBox.width / 2),
    ),
  ).toBeLessThanOrEqual(1);
  expect(collapsedToggleBox.y).toBeGreaterThan(
    logoBox.y + logoBox.height + 1,
  );

  for (const link of await sidebar
    .locator("[data-sidebar-nav-link]")
    .all()) {
    const linkBox = await getVisibleBox(link, "Collapsed navigation target");
    expect(linkBox.width).toBeGreaterThanOrEqual(39);
    expect(linkBox.width).toBeLessThanOrEqual(41);
    expect(linkBox.height).toBeGreaterThanOrEqual(39);
    expect(linkBox.height).toBeLessThanOrEqual(41);
    const iconBox = await getVisibleBox(
      link.locator("svg").first(),
      "Collapsed navigation icon",
    );
    expect(
      Math.abs(
        iconBox.x + iconBox.width / 2 - (linkBox.x + linkBox.width / 2),
      ),
    ).toBeLessThanOrEqual(1);
  }

  const maximumTransitionOverflow = await page.evaluate(() => {
    const monitor = (
      window as typeof window & {
        __offerTrackOverflowMonitor?: {
          active: boolean;
          maxOverflow: number;
        };
      }
    ).__offerTrackOverflowMonitor;

    if (!monitor) {
      throw new Error("Sidebar overflow monitor was not installed");
    }
    monitor.active = false;
    return monitor.maxOverflow;
  });
  expect(maximumTransitionOverflow).toBeLessThanOrEqual(1);
  await expectNoPageOverflow(page);

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
  await page
    .getByRole("combobox", { name: "Stage" })
    .selectOption("applied");
  await expect(
    page.getByRole("button", { name: "Clear filters" }),
  ).toBeVisible();
  await expectApplicationsToolbarLayout(page, "intermediate", true);
  await page.getByRole("button", { name: "Board" }).click();
  await expect(page.getByRole("heading", { name: "Initial" })).toBeVisible();
  await expectNoPageOverflow(page);

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
