/** @jest-environment jsdom */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  getApplication,
  listApplicationInterviews,
  listApplications,
} from "@/lib/api";
import type {
  Application,
  ApplicationsListParams,
  ApplicationsPage as ApplicationsPageResponse,
} from "@/lib/api";
import ApplicationsPage from "./page";

let currentUrl = "/applications";
const mockReplace = jest.fn((url: string) => {
  currentUrl = url;
});

jest.mock("next/navigation", () => ({
  usePathname: () => "/applications",
  useRouter: () => ({
    replace: mockReplace,
  }),
  useSearchParams: () => new URLSearchParams(currentUrl.split("?")[1] ?? ""),
}));

jest.mock("next/link", () => ({
  __esModule: true,
  default: "a",
}));

jest.mock("@/lib/api", () => {
  const actual = jest.requireActual("@/lib/api");

  return {
    ...actual,
    getApplication: jest.fn(),
    listApplicationInterviews: jest.fn(),
    listApplications: jest.fn(),
  };
});

const mockedListApplications = listApplications as jest.MockedFunction<
  typeof listApplications
>;
const mockedGetApplication = getApplication as jest.MockedFunction<
  typeof getApplication
>;
const mockedListApplicationInterviews =
  listApplicationInterviews as jest.MockedFunction<
    typeof listApplicationInterviews
  >;

const baseApplication: Application = {
  appliedAt: null,
  companyName: "Acme",
  createdAt: "2026-01-01T00:00:00.000Z",
  id: "app-1",
  jobUrl: null,
  location: null,
  nextInterview: null,
  notes: null,
  positionTitle: "Backend Engineer",
  stage: "applied",
  updatedAt: "2026-01-01T00:00:00.000Z",
  workMode: null,
};

const makeApplication = (overrides: Partial<Application> = {}): Application => ({
  ...baseApplication,
  ...overrides,
});

const makePage = (
  overrides: Partial<ApplicationsPageResponse> = {},
): ApplicationsPageResponse => ({
  items: [makeApplication()],
  page: 0,
  size: 20,
  totalItems: 1,
  totalPages: 1,
  ...overrides,
});

const installApiMocks = ({
  detailMode = "success",
  listPage = makePage(),
}: {
  detailMode?: "success" | "failure" | "pending";
  listPage?: ApplicationsPageResponse;
} = {}) => {
  mockedListApplications.mockResolvedValue(listPage);
  mockedListApplicationInterviews.mockResolvedValue([]);

  if (detailMode === "pending") {
    mockedGetApplication.mockReturnValue(
      new Promise<Application>(() => undefined),
    );
    return;
  }

  if (detailMode === "failure") {
    mockedGetApplication.mockRejectedValue(new Error("not found"));
    return;
  }

  mockedGetApplication.mockResolvedValue(makeApplication());
};

const renderPage = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
      queries: { retry: false },
    },
  });
  const makeUi = () =>
    React.createElement(
      QueryClientProvider,
      { client: queryClient },
      React.createElement(ApplicationsPage),
    );
  const view = render(makeUi());

  return {
    ...view,
    rerenderPage: () => view.rerender(makeUi()),
  };
};

const lastListParams = (): ApplicationsListParams => {
  const [params] = mockedListApplications.mock.calls.at(-1) ?? [];

  if (!params) {
    throw new Error("List request was not made");
  }

  return params;
};

const lastReplaceUrl = (): URL => {
  const [url] = mockReplace.mock.calls.at(-1) ?? [];

  if (!url) {
    throw new Error("Router replace was not called");
  }

  return new URL(String(url), "http://localhost");
};

describe("ApplicationsPage", () => {
  beforeEach(() => {
    currentUrl = "/applications";
    jest.useRealTimers();
    mockReplace.mockClear();
    mockedGetApplication.mockReset();
    mockedListApplicationInterviews.mockReset();
    mockedListApplications.mockReset();
  });

  it("sends URL params to the applications list API", async () => {
    currentUrl =
      "/applications?search=acme&stage=offer&page=2&size=10&sort=companyName&direction=asc";
    installApiMocks({
      listPage: makePage({ items: [], page: 2, size: 10, totalItems: 0, totalPages: 0 }),
    });

    renderPage();

    await waitFor(() => {
      expect(mockedListApplications).toHaveBeenCalled();
    });

    expect(lastListParams()).toEqual({
      direction: "asc",
      page: 2,
      search: "acme",
      size: 10,
      sort: "companyName",
      stage: "offer",
    });
  });

  it("searches on Enter, resets page, and omits default params", async () => {
    currentUrl = "/applications?page=3";
    installApiMocks();
    const user = userEvent.setup();
    renderPage();

    await user.type(
      await screen.findByPlaceholderText("Search company or position..."),
      "acme",
    );
    await user.keyboard("{Enter}");

    await waitFor(() => {
      const url = lastReplaceUrl();
      expect(url.pathname).toBe("/applications");
      expect(url.searchParams.get("search")).toBe("acme");
      expect(url.searchParams.get("page")).toBeNull();
    });
  });

  it("searches when the search icon is clicked", async () => {
    currentUrl = "/applications?page=2";
    installApiMocks();
    const user = userEvent.setup();
    renderPage();

    await user.type(
      await screen.findByPlaceholderText("Search company or position..."),
      "globex",
    );
    await user.click(screen.getByRole("button", { name: "Search applications" }));

    const url = lastReplaceUrl();
    expect(url.searchParams.get("search")).toBe("globex");
    expect(url.searchParams.get("page")).toBeNull();
  });

  it("clears search and refetches without search params", async () => {
    currentUrl = "/applications?search=acme&page=3&stage=applied";
    installApiMocks();
    const user = userEvent.setup();
    const view = renderPage();

    await waitFor(() => {
      expect(mockedListApplications).toHaveBeenCalled();
    });
    const initialListCalls = mockedListApplications.mock.calls.length;

    await user.click(await screen.findByRole("button", { name: "Clear search" }));

    const url = lastReplaceUrl();
    expect(url.searchParams.get("search")).toBeNull();
    expect(url.searchParams.get("page")).toBeNull();
    expect(url.searchParams.get("stage")).toBe("applied");

    view.rerenderPage();
    await waitFor(() => {
      expect(mockedListApplications.mock.calls.length).toBeGreaterThan(
        initialListCalls,
      );
    });
    expect(lastListParams()).toEqual(
      expect.objectContaining({
        search: "",
        stage: "applied",
      }),
    );
  });

  it("stage changes reset page", async () => {
    currentUrl = "/applications?page=3&search=acme";
    installApiMocks();
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions((await screen.findAllByRole("combobox"))[0], "applied");

    const url = lastReplaceUrl();
    expect(url.searchParams.get("search")).toBe("acme");
    expect(url.searchParams.get("stage")).toBe("applied");
    expect(url.searchParams.get("page")).toBeNull();
  });

  it("sort changes reset page", async () => {
    currentUrl = "/applications?page=2&direction=asc";
    installApiMocks();
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions((await screen.findAllByRole("combobox"))[1], "companyName");

    const url = lastReplaceUrl();
    expect(url.searchParams.get("sort")).toBe("companyName");
    expect(url.searchParams.get("direction")).toBe("asc");
    expect(url.searchParams.get("page")).toBeNull();
  });

  it("pagination updates page while preserving filters", async () => {
    currentUrl = "/applications?page=1&search=acme";
    installApiMocks({
      listPage: makePage({ page: 1, totalItems: 3, totalPages: 3 }),
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Next" }));

    const url = lastReplaceUrl();
    expect(url.searchParams.get("search")).toBe("acme");
    expect(url.searchParams.get("page")).toBe("2");
  });

  it("removes only page when the current page is out of range", async () => {
    currentUrl =
      "/applications?search=acme&stage=applied&page=99&size=10&sort=companyName&direction=asc&id=app-1";
    installApiMocks({
      detailMode: "pending",
      listPage: makePage({
        items: [],
        page: 99,
        size: 10,
        totalItems: 11,
        totalPages: 2,
      }),
    });

    renderPage();

    await waitFor(() => {
      const url = lastReplaceUrl();
      expect(url.searchParams.get("page")).toBeNull();
      expect(url.searchParams.get("search")).toBe("acme");
      expect(url.searchParams.get("stage")).toBe("applied");
      expect(url.searchParams.get("size")).toBe("10");
      expect(url.searchParams.get("sort")).toBe("companyName");
      expect(url.searchParams.get("direction")).toBe("asc");
      expect(url.searchParams.get("id")).toBe("app-1");
    });
  });

  it("fetches page 0 and removes malformed page params", async () => {
    currentUrl =
      "/applications?search=acme&stage=applied&page=abc&size=10&sort=companyName&direction=asc&id=app-1";
    installApiMocks({
      detailMode: "pending",
      listPage: makePage({
        items: [],
        page: 0,
        size: 10,
        totalItems: 11,
        totalPages: 2,
      }),
    });

    renderPage();

    await waitFor(() => {
      expect(mockedListApplications).toHaveBeenCalled();
    });
    expect(lastListParams().page).toBe(0);

    await waitFor(() => {
      const url = lastReplaceUrl();
      expect(url.searchParams.get("page")).toBeNull();
      expect(url.searchParams.get("search")).toBe("acme");
      expect(url.searchParams.get("stage")).toBe("applied");
      expect(url.searchParams.get("size")).toBe("10");
      expect(url.searchParams.get("sort")).toBe("companyName");
      expect(url.searchParams.get("direction")).toBe("asc");
      expect(url.searchParams.get("id")).toBe("app-1");
    });
  });

  it("opening a list item adds id and opens the modal after detail fetch", async () => {
    installApiMocks();
    const user = userEvent.setup();
    const view = renderPage();

    await user.click((await screen.findAllByRole("button", { name: /edit/i }))[0]);

    expect(lastReplaceUrl().searchParams.get("id")).toBe("app-1");

    view.rerenderPage();

    expect(
      await screen.findByRole("heading", { name: "Edit application" }),
    ).toBeTruthy();
  });

  it("direct id fetch opens the modal after success", async () => {
    currentUrl = "/applications?id=app-1";
    installApiMocks();

    renderPage();

    expect(
      await screen.findByRole("heading", { name: "Edit application" }),
    ).toBeTruthy();
    expect(screen.getByDisplayValue("Acme")).toBeTruthy();
  });

  it("shows loading while direct id fetch is pending", async () => {
    currentUrl = "/applications?id=app-1";
    installApiMocks({ detailMode: "pending" });

    renderPage();

    expect(await screen.findByText("Loading application...")).toBeTruthy();
    expect(screen.queryByRole("heading", { name: "Edit application" })).toBeNull();
  });

  it("does not crash or open the modal when direct id fetch fails", async () => {
    currentUrl = "/applications?id=app-1";
    installApiMocks({ detailMode: "failure" });

    renderPage();

    expect(
      await screen.findByText(
        "Unable to open this application. It may have been deleted or you may not have access.",
      ),
    ).toBeTruthy();
    expect(screen.queryByRole("heading", { name: "Edit application" })).toBeNull();
  });

  it("closing the modal removes only id from a filtered URL", async () => {
    currentUrl =
      "/applications?search=acme&stage=applied&page=2&sort=companyName&direction=asc&id=app-1";
    installApiMocks({
      listPage: makePage({ page: 2, totalItems: 3, totalPages: 3 }),
    });
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("heading", { name: "Edit application" });
    await user.click(screen.getByRole("button", { name: "Close modal" }));

    const url = lastReplaceUrl();
    expect(url.searchParams.get("id")).toBeNull();
    expect(url.searchParams.get("search")).toBe("acme");
    expect(url.searchParams.get("stage")).toBe("applied");
    expect(url.searchParams.get("page")).toBe("2");
    expect(url.searchParams.get("sort")).toBe("companyName");
    expect(url.searchParams.get("direction")).toBe("asc");
  });
});
