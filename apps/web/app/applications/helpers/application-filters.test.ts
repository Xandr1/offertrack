import { Application } from "@/lib/api";
import {
  filterAndSortApplications,
  parseSearchQueryParam,
  parseSortParam,
  parseStageFilterParam,
} from "./application-filters";

const baseApplication = (overrides: Partial<Application>): Application => ({
  appliedAt: null,
  companyName: "Company",
  createdAt: "2026-01-01T00:00:00.000Z",
  id: "app",
  location: null,
  nextInterview: null,
  notes: null,
  positionTitle: "Engineer",
  stage: "initial",
  updatedAt: "2026-01-01T00:00:00.000Z",
  workMode: null,
  ...overrides,
});

describe("application-filters", () => {
  it("parses stage and sort params with defaults", () => {
    expect(parseStageFilterParam("offer")).toBe("offer");
    expect(parseStageFilterParam("invalid")).toBe("all");
    expect(parseSortParam("created_asc")).toBe("created_asc");
    expect(parseSortParam("invalid")).toBe("updated_desc");
    expect(parseSearchQueryParam("  acme  ")).toBe("acme");
    expect(parseSearchQueryParam(null)).toBe("");
  });

  it("filters by stage and search query", () => {
    const applications = [
      baseApplication({
        companyName: "Acme",
        id: "1",
        positionTitle: "Frontend Engineer",
        stage: "interviewing",
      }),
      baseApplication({
        companyName: "Beta",
        id: "2",
        positionTitle: "Data Analyst",
        stage: "offer",
      }),
    ];

    const result = filterAndSortApplications({
      applications,
      searchQuery: "front",
      sort: "updated_desc",
      stageFilter: "interviewing",
    });

    expect(result.map((item) => item.id)).toEqual(["1"]);
  });

  it("sorts applications according to selected mode", () => {
    const applications = [
      baseApplication({
        createdAt: "2026-01-01T00:00:00.000Z",
        id: "1",
        updatedAt: "2026-01-03T00:00:00.000Z",
      }),
      baseApplication({
        createdAt: "2026-01-03T00:00:00.000Z",
        id: "2",
        updatedAt: "2026-01-02T00:00:00.000Z",
      }),
      baseApplication({
        createdAt: "2026-01-02T00:00:00.000Z",
        id: "3",
        updatedAt: "2026-01-01T00:00:00.000Z",
      }),
    ];

    expect(
      filterAndSortApplications({
        applications,
        searchQuery: "",
        sort: "updated_desc",
        stageFilter: "all",
      }).map((item) => item.id),
    ).toEqual(["1", "2", "3"]);

    expect(
      filterAndSortApplications({
        applications,
        searchQuery: "",
        sort: "updated_asc",
        stageFilter: "all",
      }).map((item) => item.id),
    ).toEqual(["3", "2", "1"]);

    expect(
      filterAndSortApplications({
        applications,
        searchQuery: "",
        sort: "created_desc",
        stageFilter: "all",
      }).map((item) => item.id),
    ).toEqual(["2", "3", "1"]);
  });
});
