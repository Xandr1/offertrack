/** @jest-environment jsdom */

import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  ApiError,
  createApplication,
  createApplicationDraft,
  deleteApplication,
  getApplication,
  getCurrentUser,
  getApplicationBoardColumn,
  getApplicationsBoard,
  listApplicationInterviews,
  listApplications,
  replaceApplication,
} from "@/lib/api";
import type {
  Application,
  ApplicationDraftResponse,
  ApplicationWithInterviews,
  ApplicationsListParams,
  ApplicationsPage as ApplicationsPageResponse,
  ApplicationBoard,
} from "@/lib/api";
import {
  getStoredApplicationsView,
  storeApplicationsView,
} from "./helpers/application-filters";
import ApplicationsPage from "./page";
import { LoginClient } from "../login/login-client";
import { queryKeys } from "@/lib/query-keys";

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
    createApplication: jest.fn(),
    createApplicationDraft: jest.fn(),
    deleteApplication: jest.fn(),
    getApplication: jest.fn(),
    getCurrentUser: jest.fn(),
    getApplicationBoardColumn: jest.fn(),
    getApplicationsBoard: jest.fn(),
    listApplicationInterviews: jest.fn(),
    listApplications: jest.fn(),
    replaceApplication: jest.fn(),
  };
});

const mockedListApplications = listApplications as jest.MockedFunction<
  typeof listApplications
>;
const mockedCreateApplication = createApplication as jest.MockedFunction<
  typeof createApplication
>;
const mockedCreateApplicationDraft = createApplicationDraft as jest.MockedFunction<
  typeof createApplicationDraft
>;
const mockedDeleteApplication = deleteApplication as jest.MockedFunction<
  typeof deleteApplication
>;
const mockedGetApplication = getApplication as jest.MockedFunction<
  typeof getApplication
>;
const mockedGetCurrentUser = getCurrentUser as jest.MockedFunction<
  typeof getCurrentUser
>;
const mockedGetApplicationBoardColumn =
  getApplicationBoardColumn as jest.MockedFunction<
    typeof getApplicationBoardColumn
  >;
const mockedGetApplicationsBoard = getApplicationsBoard as jest.MockedFunction<
  typeof getApplicationsBoard
>;
const mockedListApplicationInterviews =
  listApplicationInterviews as jest.MockedFunction<
    typeof listApplicationInterviews
  >;
const mockedReplaceApplication = replaceApplication as jest.MockedFunction<
  typeof replaceApplication
>;

const baseApplication: Application = {
  appliedAt: null,
  companyName: "Acme",
  createdAt: "2026-01-01T00:00:00.000Z",
  id: "app-1",
  jobUrl: null,
  location: null,
  followedUpAt: null,
  lastInterview: null,
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

const makeApplicationWithInterviews = (
  application: Application = makeApplication(),
): ApplicationWithInterviews => ({
  application,
  interviews: [],
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

const makeBoard = (overrides: Partial<ApplicationBoard> = {}): ApplicationBoard => ({
  columns: [
    {
      stage: "initial",
      totalCount: 0,
      items: [],
      nextOffset: 0,
      hasMore: false,
    },
    {
      stage: "applied",
      totalCount: 1,
      items: [makeApplication()],
      nextOffset: 1,
      hasMore: false,
    },
    {
      stage: "interviewing",
      totalCount: 0,
      items: [],
      nextOffset: 0,
      hasMore: false,
    },
    {
      stage: "offer",
      totalCount: 0,
      items: [],
      nextOffset: 0,
      hasMore: false,
    },
    {
      stage: "rejected",
      totalCount: 0,
      items: [],
      nextOffset: 0,
      hasMore: false,
    },
  ],
  ...overrides,
});

const installApiMocks = ({
  detailMode = "success",
  listPage = makePage(),
}: {
  detailMode?: "success" | "failure" | "pending";
  listPage?: ApplicationsPageResponse;
} = {}) => {
  mockedGetCurrentUser.mockResolvedValue({
    id: "user-1",
    email: "person@example.com",
    name: null,
  });
  mockedListApplications.mockResolvedValue(listPage);
  mockedGetApplicationsBoard.mockResolvedValue(makeBoard());
  mockedGetApplicationBoardColumn.mockResolvedValue({
    stage: "applied",
    totalCount: 1,
    items: [],
    nextOffset: 1,
    hasMore: false,
  });
  mockedListApplicationInterviews.mockResolvedValue([]);
  mockedCreateApplication.mockResolvedValue(makeApplicationWithInterviews());
  mockedDeleteApplication.mockResolvedValue();
  mockedReplaceApplication.mockResolvedValue(makeApplicationWithInterviews());

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

const makeDraft = (
  overrides: Partial<ApplicationDraftResponse> = {},
): ApplicationDraftResponse => ({
  companyName: "Globex",
  positionTitle: "Senior Product Engineer",
  jobUrl: "https://example.com/jobs/123",
  location: "Remote",
  workMode: "remote",
  stage: "initial",
  notes:
    "Globex is hiring a senior product engineer for platform work. The role is remote.",
  interviews: [
    {
      type: "recruiter",
      status: "initial",
      scheduledAt: null,
    },
  ],
  warnings: [],
  ...overrides,
});

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
    queryClient,
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
    window.localStorage.clear();
    jest.useRealTimers();
    mockReplace.mockClear();
    mockedCreateApplication.mockReset();
    mockedCreateApplicationDraft.mockReset();
    mockedDeleteApplication.mockReset();
    mockedGetApplication.mockReset();
    mockedGetCurrentUser.mockReset();
    mockedGetApplicationBoardColumn.mockReset();
    mockedGetApplicationsBoard.mockReset();
    mockedListApplicationInterviews.mockReset();
    mockedListApplications.mockReset();
    mockedReplaceApplication.mockReset();
  });

  it("does not render or query protected applications before fresh auth succeeds", async () => {
    installApiMocks();
    let resolveSession: (value: {
      id: string;
      email: string;
      name: null;
    }) => void = () => undefined;
    mockedGetCurrentUser.mockReturnValue(
      new Promise((resolve) => {
        resolveSession = resolve;
      }),
    );

    renderPage();

    expect(screen.getByText("Loading applications...")).toBeTruthy();
    expect(screen.queryByRole("heading", { name: "Applications" })).toBeNull();
    expect(mockedListApplications).not.toHaveBeenCalled();
    expect(mockedGetApplicationsBoard).not.toHaveBeenCalled();

    resolveSession({ id: "user-1", email: "person@example.com", name: null });

    expect(
      await screen.findByRole("heading", { name: "Applications" }),
    ).toBeTruthy();
    await waitFor(() => expect(mockedListApplications).toHaveBeenCalled());
  });

  it("redirects without flashing protected applications after fresh auth fails", async () => {
    installApiMocks();
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));

    renderPage();

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login"));
    expect(screen.queryByRole("heading", { name: "Applications" })).toBeNull();
    expect(mockedListApplications).not.toHaveBeenCalled();
    expect(mockedGetApplicationsBoard).not.toHaveBeenCalled();
  });

  it("sends URL params to the applications list API", async () => {
    currentUrl =
      "/applications?search=acme&stage=offer&page=2&size=10&sort=createdAt&direction=asc";
    installApiMocks({
      listPage: makePage({
        items: [],
        page: 2,
        size: 10,
        totalItems: 21,
        totalPages: 3,
      }),
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
      sort: "createdAt",
      stage: "offer",
    });
    expect(screen.getAllByRole("combobox")).toHaveLength(3);
    expect(screen.getByRole("option", { name: "Updated" })).toBeTruthy();
    expect(screen.getByRole("option", { name: "Created" })).toBeTruthy();
    expect(screen.queryByRole("option", { name: "Company" })).toBeNull();
    expect(screen.queryByRole("option", { name: "Position" })).toBeNull();
    expect(mockedGetApplicationsBoard).not.toHaveBeenCalled();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("defaults to list when storage is missing and ignores a legacy view URL param", async () => {
    currentUrl = "/applications?view=board&search=react&stage=applied";
    installApiMocks();

    renderPage();

    await waitFor(() => expect(mockedListApplications).toHaveBeenCalled());
    await waitFor(() =>
      expect(`${lastReplaceUrl().pathname}${lastReplaceUrl().search}`).toBe(
        "/applications?search=react&stage=applied",
      ),
    );
    expect(mockedGetApplicationsBoard).not.toHaveBeenCalled();
    expect(getStoredApplicationsView()).toBe("list");
  });

  it("removes invalid legacy sorting from the initial list URL", async () => {
    currentUrl = "/applications?sort=companyName&direction=asc";
    installApiMocks();

    renderPage();

    await waitFor(() => expect(mockedListApplications).toHaveBeenCalled());
    await waitFor(() =>
      expect(`${lastReplaceUrl().pathname}${lastReplaceUrl().search}`).toBe(
        "/applications",
      ),
    );
    await waitFor(() =>
      expect(lastListParams()).toEqual(
        expect.objectContaining({ sort: "updatedAt", direction: "desc" }),
      ),
    );
  });

  it("stores board view and keeps only canonical board URL params", async () => {
    currentUrl =
      "/applications?search=acme&id=app-1&stage=offer&page=2&size=10&sort=createdAt&direction=asc";
    installApiMocks({
      detailMode: "pending",
      listPage: makePage({ page: 2, totalPages: 3 }),
    });
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("Acme");
    await user.click(screen.getByRole("button", { name: "Board" }));

    const url = lastReplaceUrl();
    expect(getStoredApplicationsView()).toBe("board");
    expect(url.searchParams.get("view")).toBeNull();
    expect(url.searchParams.get("search")).toBe("acme");
    expect(url.searchParams.get("id")).toBe("app-1");
    expect(url.searchParams.get("sort")).toBe("createdAt");
    expect(url.searchParams.get("direction")).toBe("asc");
    expect(url.searchParams.get("stage")).toBe("offer");
    expect(url.searchParams.get("page")).toBeNull();
    expect(url.searchParams.get("size")).toBeNull();
  });

  it("removes invalid legacy sort and direction when selecting board", async () => {
    currentUrl =
      "/applications?search=react&sort=companyName&direction=asc&page=3";
    installApiMocks({
      listPage: makePage({ page: 3, totalPages: 4 }),
    });
    const user = userEvent.setup();

    renderPage();
    await screen.findByText("Acme");
    await user.click(screen.getByRole("button", { name: "Board" }));

    const url = lastReplaceUrl();
    expect(`${url.pathname}${url.search}`).toBe("/applications?search=react");
  });

  it("keeps shared params when selecting list after initial board cleanup", async () => {
    currentUrl =
      "/applications?search=react&id=app-1&stage=applied&page=2&size=10&sort=createdAt&direction=asc&view=board";
    storeApplicationsView("board");
    installApiMocks({
      detailMode: "pending",
      listPage: makePage({ page: 2, size: 10, totalPages: 3 }),
    });
    const user = userEvent.setup();

    renderPage();
    await screen.findByRole("heading", { name: "Initial" });
    await user.click(screen.getByRole("button", { name: "List" }));

    const url = lastReplaceUrl();
    expect(getStoredApplicationsView()).toBe("list");
    expect(url.searchParams.get("view")).toBeNull();
    expect(url.searchParams.get("search")).toBe("react");
    expect(url.searchParams.get("id")).toBe("app-1");
    expect(url.searchParams.get("stage")).toBe("applied");
    expect(url.searchParams.get("page")).toBeNull();
    expect(url.searchParams.get("size")).toBeNull();
    expect(url.searchParams.get("sort")).toBe("createdAt");
    expect(url.searchParams.get("direction")).toBe("asc");
  });

  it("cleans list-only params after loading board view from storage", async () => {
    currentUrl = "/applications?stage=offer&page=2&size=10&search=acme";
    storeApplicationsView("board");
    installApiMocks();

    renderPage();

    await screen.findByRole("heading", { name: "Initial" });
    await waitFor(() =>
      expect(`${lastReplaceUrl().pathname}${lastReplaceUrl().search}`).toBe(
        "/applications?search=acme&stage=offer",
      ),
    );
    expect(mockedGetApplicationsBoard).toHaveBeenCalledWith({
      search: "acme",
      stage: "offer",
      sort: "updatedAt",
      direction: "desc",
    });
    expect(mockedListApplications).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { name: "Applied" })).toBeTruthy();
    expect(screen.getByText("Backend Engineer")).toBeTruthy();
    expect(screen.getByText("Showing 1 of 1")).toBeTruthy();
    expect(screen.getAllByText("Showing 0 of 0")).toHaveLength(4);
    expect(
      screen.getByRole("button", { name: "Move Acme application" }),
    ).toBeTruthy();
    expect(screen.getAllByRole("combobox")).toHaveLength(3);
    expect(
      screen.getByRole("option", { name: "Updated" }),
    ).toBeTruthy();
    expect(screen.getByRole("option", { name: "Created" })).toBeTruthy();
    expect(screen.queryByRole("option", { name: "Company" })).toBeNull();
    expect(screen.queryByRole("option", { name: "Position" })).toBeNull();
    expect(mockReplace).toHaveBeenCalledTimes(1);
  });

  it("keeps valid board sorting when cleaning the initial URL", async () => {
    currentUrl =
      "/applications?search=react&sort=createdAt&direction=asc&stage=interviewing&page=2";
    storeApplicationsView("board");
    installApiMocks();

    renderPage();

    await screen.findByRole("heading", { name: "Initial" });
    await waitFor(() =>
      expect(`${lastReplaceUrl().pathname}${lastReplaceUrl().search}`).toBe(
        "/applications?search=react&stage=interviewing&sort=createdAt&direction=asc",
      ),
    );
    expect(mockedGetApplicationsBoard).toHaveBeenCalledWith({
      search: "react",
      stage: "interviewing",
      sort: "createdAt",
      direction: "asc",
    });
    expect(mockedListApplications).not.toHaveBeenCalled();
  });

  it("removes invalid legacy sorting when cleaning the initial board URL", async () => {
    currentUrl =
      "/applications?search=react&sort=companyName&direction=asc&page=3";
    storeApplicationsView("board");
    installApiMocks();

    renderPage();

    await screen.findByRole("heading", { name: "Initial" });
    await waitFor(() =>
      expect(`${lastReplaceUrl().pathname}${lastReplaceUrl().search}`).toBe(
        "/applications?search=react",
      ),
    );
    expect(mockedListApplications).not.toHaveBeenCalled();
  });

  it("loads more into only the selected board column", async () => {
    storeApplicationsView("board");
    const initialBoard = makeBoard();
    initialBoard.columns[1] = {
      ...initialBoard.columns[1],
      totalCount: 2,
      hasMore: true,
    };
    installApiMocks();
    mockedGetApplicationsBoard.mockResolvedValue(initialBoard);
    mockedGetApplicationBoardColumn.mockResolvedValue({
      stage: "applied",
      totalCount: 2,
      items: [
        makeApplication({
          id: "app-2",
          companyName: "Globex",
          positionTitle: "Platform Engineer",
        }),
      ],
      nextOffset: 2,
      hasMore: false,
    });
    const user = userEvent.setup();

    renderPage();
    await user.click(await screen.findByRole("button", { name: "Load more" }));

    await screen.findByText("Globex");
    expect(mockedGetApplicationBoardColumn).toHaveBeenCalledWith({
      columnStage: "applied",
      search: "",
      stage: null,
      sort: "updatedAt",
      direction: "desc",
      offset: 1,
    });
    expect(screen.getAllByText("No applications")).toHaveLength(4);
  });

  it("updates board sorting without restoring list-only URL params", async () => {
    currentUrl = "/applications?search=acme&stage=offer&page=2&size=10";
    storeApplicationsView("board");
    installApiMocks();
    const user = userEvent.setup();
    const view = renderPage();

    await screen.findByRole("heading", { name: "Initial" });
    await user.selectOptions(screen.getAllByRole("combobox")[1], "createdAt");

    let url = lastReplaceUrl();
    expect(url.searchParams.get("sort")).toBe("createdAt");
    expect(url.searchParams.get("stage")).toBe("offer");
    expect(url.searchParams.get("page")).toBeNull();
    expect(url.searchParams.get("size")).toBeNull();
    expect(url.searchParams.get("view")).toBeNull();

    view.rerenderPage();
    await user.selectOptions(screen.getAllByRole("combobox")[2], "asc");
    view.rerenderPage();

    url = lastReplaceUrl();
    expect(url.searchParams.get("sort")).toBe("createdAt");
    expect(url.searchParams.get("direction")).toBe("asc");
    await waitFor(() =>
      expect(mockedGetApplicationsBoard).toHaveBeenCalledWith({
        search: "acme",
        stage: "offer",
        sort: "createdAt",
        direction: "asc",
      }),
    );
  });

  it("opens the existing delete confirmation from a board card", async () => {
    storeApplicationsView("board");
    installApiMocks();
    const user = userEvent.setup();

    renderPage();
    await user.click(await screen.findByRole("button", { name: "Delete" }));

    expect(screen.getByText("Delete application?")).toBeTruthy();
    expect(
      screen.getByText(
        "This will remove the application and all its interview rounds.",
      ),
    ).toBeTruthy();
  });

  it("refreshes board data after a successful board delete", async () => {
    currentUrl = "/applications?sort=createdAt&direction=asc";
    storeApplicationsView("board");
    installApiMocks();
    const user = userEvent.setup();

    renderPage();
    await user.click(await screen.findByRole("button", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: "Delete application" }));

    await waitFor(() => expect(mockedDeleteApplication).toHaveBeenCalledWith("app-1"));
    await waitFor(() => expect(mockedGetApplicationsBoard).toHaveBeenCalledTimes(2));
    expect(mockedGetApplicationsBoard).toHaveBeenLastCalledWith({
      search: "",
      stage: null,
      sort: "createdAt",
      direction: "asc",
    });
    expect(screen.queryByText("Delete application?")).toBeNull();
  });

  it("keeps delete successful and shows a page error when board refresh fails", async () => {
    storeApplicationsView("board");
    installApiMocks();
    mockedGetApplicationsBoard
      .mockResolvedValueOnce(makeBoard())
      .mockRejectedValueOnce(new Error("refresh failed"));
    const user = userEvent.setup();

    renderPage();
    await user.click(await screen.findByRole("button", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: "Delete application" }));

    expect(
      await screen.findByText("Something went wrong. Please try again."),
    ).toBeTruthy();
    expect(mockedDeleteApplication).toHaveBeenCalledWith("app-1");
    expect(screen.queryByText("Delete application?")).toBeNull();
  });

  it("closes edit and refreshes board data after a successful board save", async () => {
    currentUrl = "/applications?sort=createdAt&direction=asc";
    storeApplicationsView("board");
    installApiMocks();
    const user = userEvent.setup();
    const view = renderPage();

    await user.click(await screen.findByRole("button", { name: "Edit" }));
    view.rerenderPage();
    await screen.findByRole("heading", { name: "Edit application" });
    const saveButton = await screen.findByRole("button", { name: "Save changes" });
    await waitFor(() => expect((saveButton as HTMLButtonElement).disabled).toBe(false));
    await user.click(saveButton);

    await waitFor(() => expect(mockedReplaceApplication).toHaveBeenCalled());
    await waitFor(() => expect(mockedGetApplicationsBoard).toHaveBeenCalledTimes(2));
    expect(mockedGetApplicationsBoard).toHaveBeenLastCalledWith({
      search: "",
      stage: null,
      sort: "createdAt",
      direction: "asc",
    });
    expect(screen.queryByRole("heading", { name: "Edit application" })).toBeNull();
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

    await user.selectOptions((await screen.findAllByRole("combobox"))[1], "createdAt");

    const url = lastReplaceUrl();
    expect(url.searchParams.get("sort")).toBe("createdAt");
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

    const nextButton = await screen.findByRole("button", { name: "Next" });
    await waitFor(() =>
      expect((nextButton as HTMLButtonElement).disabled).toBe(false),
    );
    await user.click(nextButton);

    const url = lastReplaceUrl();
    expect(url.searchParams.get("search")).toBe("acme");
    expect(url.searchParams.get("page")).toBe("2");
  });

  it("removes only page when the current page is out of range", async () => {
    currentUrl =
      "/applications?search=acme&stage=applied&page=99&size=10&sort=createdAt&direction=asc&id=app-1";
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
      expect(url.searchParams.get("sort")).toBe("createdAt");
      expect(url.searchParams.get("direction")).toBe("asc");
      expect(url.searchParams.get("id")).toBe("app-1");
    });
  });

  it("fetches page 0 and removes malformed page params", async () => {
    currentUrl =
      "/applications?search=acme&stage=applied&page=abc&size=10&sort=createdAt&direction=asc&id=app-1";
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
      expect(url.searchParams.get("sort")).toBe("createdAt");
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
      "/applications?search=acme&stage=applied&page=2&sort=createdAt&direction=asc&id=app-1";
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
    expect(url.searchParams.get("sort")).toBe("createdAt");
    expect(url.searchParams.get("direction")).toBe("asc");
  });

  it("saving the selected edit modal removes id from the URL", async () => {
    currentUrl = "/applications?search=acme&id=app-1";
    installApiMocks();
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("heading", { name: "Edit application" });
    const saveButton = await screen.findByRole("button", { name: "Save changes" });
    await waitFor(() => expect((saveButton as HTMLButtonElement).disabled).toBe(false));
    await user.click(saveButton);

    await waitFor(() => expect(mockedReplaceApplication).toHaveBeenCalled());
    await waitFor(() =>
      expect(screen.queryByRole("heading", { name: "Edit application" })).toBeNull(),
    );
    const url = lastReplaceUrl();
    expect(url.searchParams.get("id")).toBeNull();
    expect(url.searchParams.get("search")).toBe("acme");
  });

  it("deleting the selected application closes edit and removes id from the URL", async () => {
    currentUrl = "/applications?search=acme&id=app-1";
    installApiMocks();
    const user = userEvent.setup();
    renderPage();

    await screen.findByRole("heading", { name: "Edit application" });
    await user.click(screen.getAllByRole("button", { name: "Delete" })[0]);
    await user.click(screen.getByRole("button", { name: "Delete application" }));

    await waitFor(() => expect(mockedDeleteApplication).toHaveBeenCalledWith("app-1"));
    await waitFor(() =>
      expect(screen.queryByRole("heading", { name: "Edit application" })).toBeNull(),
    );
    const url = lastReplaceUrl();
    expect(url.searchParams.get("id")).toBeNull();
    expect(url.searchParams.get("search")).toBe("acme");
  });

  it("opens the Create with AI modal", async () => {
    installApiMocks();
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Create with AI" }));

    expect(
      screen.getByRole("heading", { name: "Create with AI" }),
    ).toBeTruthy();
    expect(screen.getByPlaceholderText("https://company.com/jobs/123")).toBeTruthy();
  });

  it("handles empty and invalid AI job URLs safely", async () => {
    installApiMocks();
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Create with AI" }));
    await user.click(screen.getByRole("button", { name: "Generate draft" }));

    expect(screen.getByText("Enter a job URL.")).toBeTruthy();

    await user.type(
      screen.getByPlaceholderText("https://company.com/jobs/123"),
      "ftp://example.com/jobs/123",
    );
    await user.click(screen.getByRole("button", { name: "Generate draft" }));

    expect(screen.getByText("Enter a valid http or https job URL.")).toBeTruthy();
    expect(mockedCreateApplicationDraft).not.toHaveBeenCalled();
  });

  it("shows loading while generating an AI draft and ignores the result after close", async () => {
    installApiMocks();
    const user = userEvent.setup();
    let resolveDraft: (draft: ApplicationDraftResponse) => void = () => undefined;
    mockedCreateApplicationDraft.mockReturnValue(
      new Promise<ApplicationDraftResponse>((resolve) => {
        resolveDraft = resolve;
      }),
    );
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Create with AI" }));
    await user.type(
      screen.getByPlaceholderText("https://company.com/jobs/123"),
      "https://example.com/jobs/123",
    );
    await user.click(screen.getByRole("button", { name: "Generate draft" }));

    expect(
      (screen.getByRole("button", { name: "Generating..." }) as HTMLButtonElement)
        .disabled,
    ).toBe(true);

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    await user.click(screen.getByRole("button", { name: "Create with AI" }));
    expect(
      (screen.getByRole("button", { name: "Generate draft" }) as HTMLButtonElement)
        .disabled,
    ).toBe(false);
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    resolveDraft(makeDraft());

    await waitFor(() => {
      expect(
        screen.queryByRole("heading", { name: "Create application" }),
      ).toBeNull();
    });
  });

  it("shows a safe error when draft generation fails", async () => {
    installApiMocks();
    mockedCreateApplicationDraft.mockRejectedValue(
      new ApiError(502, '{"code":"AI_SERVICE_UNAVAILABLE"}'),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Create with AI" }));
    await user.type(
      screen.getByPlaceholderText("https://company.com/jobs/123"),
      "https://example.com/jobs/123",
    );
    await user.click(screen.getByRole("button", { name: "Generate draft" }));

    expect(
      await screen.findByText(
        "AI draft generation is temporarily unavailable. Try again.",
      ),
    ).toBeTruthy();
    expect(screen.getByRole("heading", { name: "Create with AI" })).toBeTruthy();
    expect(screen.queryByRole("heading", { name: "Create application" })).toBeNull();
    expect(mockedCreateApplication).not.toHaveBeenCalled();
  });

  it("clears session caches after an action 401 and does not bounce from login", async () => {
    installApiMocks();
    mockedCreateApplicationDraft.mockRejectedValue(
      new ApiError(401, "Unauthorized"),
    );
    const user = userEvent.setup();
    const view = renderPage();

    await screen.findByRole("heading", { name: "Applications" });
    view.queryClient.setQueryData(queryKeys.dashboardSummary, { stale: true });
    await user.click(screen.getByRole("button", { name: "Create with AI" }));
    await user.type(
      screen.getByPlaceholderText("https://company.com/jobs/123"),
      "https://example.com/jobs/123",
    );
    await user.click(screen.getByRole("button", { name: "Generate draft" }));

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login"));
    expect(view.queryClient.getQueryData(queryKeys.authMe)).toBeUndefined();
    expect(
      view.queryClient.getQueryData(queryKeys.dashboardSummary),
    ).toBeUndefined();

    view.unmount();
    mockReplace.mockClear();
    mockedGetCurrentUser.mockRejectedValue(new ApiError(401, "Unauthorized"));
    render(
      React.createElement(
        QueryClientProvider,
        { client: view.queryClient },
        React.createElement(LoginClient),
      ),
    );

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeTruthy();
    expect(mockReplace).not.toHaveBeenCalledWith("/dashboard");
  });

  it("opens the application modal prefilled with an AI draft and warnings", async () => {
    installApiMocks();
    mockedCreateApplicationDraft.mockResolvedValue(
      makeDraft({
        warnings: ["Company name was inferred from page metadata."],
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Create with AI" }));
    await user.type(
      screen.getByPlaceholderText("https://company.com/jobs/123"),
      "example.com/jobs/123",
    );
    await user.click(screen.getByRole("button", { name: "Generate draft" }));

    expect(
      await screen.findByRole("heading", { name: "Create application" }),
    ).toBeTruthy();
    expect(mockedCreateApplicationDraft.mock.calls[0]?.[0]).toEqual({
      jobUrl: "https://example.com/jobs/123",
    });
    expect(screen.getByDisplayValue("Globex")).toBeTruthy();
    expect(screen.getByDisplayValue("Senior Product Engineer")).toBeTruthy();
    expect(screen.getByDisplayValue("https://example.com/jobs/123")).toBeTruthy();
    expect(screen.getAllByDisplayValue("Remote")).toHaveLength(2);
    expect(
      screen.getByDisplayValue(
        "Globex is hiring a senior product engineer for platform work. The role is remote.",
      ),
    ).toBeTruthy();
    expect(screen.getByDisplayValue("Recruiter")).toBeTruthy();
    expect(screen.getAllByDisplayValue("Initial")).toHaveLength(2);
    expect(screen.getByText("AI draft warnings")).toBeTruthy();
    expect(
      screen.getByText("Company name was inferred from page metadata."),
    ).toBeTruthy();
    expect(mockedCreateApplication).not.toHaveBeenCalled();
  });

  it("manual Add application still opens an empty create modal", async () => {
    installApiMocks();
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Add application" }));

    expect(
      await screen.findByRole("heading", { name: "Create application" }),
    ).toBeTruthy();
    expect(screen.queryByText("AI draft warnings")).toBeNull();
    expect(screen.queryByDisplayValue("Globex")).toBeNull();
  });
});
