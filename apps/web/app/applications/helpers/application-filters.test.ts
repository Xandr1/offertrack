import {
  canonicalizeApplicationsViewParams,
  parseDirectionParam,
  parsePageParam,
  parseSearchQueryParam,
  parseSizeParam,
  parseSortParam,
  parseStoredApplicationsView,
  parseStageFilterParam,
  writeApplicationsListParams,
} from "./application-filters";

describe("application-filters", () => {
  it("parses list query params with defaults", () => {
    expect(parseStageFilterParam("offer")).toBe("offer");
    expect(parseStageFilterParam("invalid")).toBe("all");
    expect(parseSortParam("createdAt")).toBe("createdAt");
    expect(parseSortParam("companyName")).toBe("updatedAt");
    expect(parseSortParam("positionTitle")).toBe("updatedAt");
    expect(parseSortParam("stage")).toBe("updatedAt");
    expect(parseSortParam("invalid")).toBe("updatedAt");
    expect(parseDirectionParam("asc")).toBe("asc");
    expect(parseDirectionParam("invalid")).toBe("desc");
    expect(parsePageParam("2")).toBe(2);
    expect(parsePageParam("-1")).toBe(0);
    expect(parseSizeParam("50")).toBe(50);
    expect(parseSizeParam("101")).toBe(20);
    expect(parseSearchQueryParam("  acme  ")).toBe("acme");
    expect(parseSearchQueryParam(null)).toBe("");
    expect(parseStoredApplicationsView("board")).toBe("board");
    expect(parseStoredApplicationsView("invalid")).toBe("list");
  });

  it("omits default list query params when writing URLs", () => {
    const params = new URLSearchParams("id=app-1");

    writeApplicationsListParams(params, {
      direction: "desc",
      page: 0,
      search: "",
      size: 20,
      sort: "updatedAt",
      stage: null,
    });

    expect(params.toString()).toBe("id=app-1");
  });

  it("writes non-default list query params while preserving unrelated params", () => {
    const params = new URLSearchParams("id=app-1");

    writeApplicationsListParams(params, {
      direction: "asc",
      page: 2,
      search: " acme ",
      size: 50,
      sort: "createdAt",
      stage: "applied",
    });

    expect(params.toString()).toBe(
      "id=app-1&page=2&size=50&search=acme&stage=applied&sort=createdAt&direction=asc",
    );
  });

  it("keeps only board-supported params and valid non-default sorting", () => {
    const params = new URLSearchParams(
      "search=react&id=app-1&stage=interviewing&page=2&size=50&sort=createdAt&direction=asc&view=list&unknown=value",
    );

    expect(canonicalizeApplicationsViewParams(params, "board").toString()).toBe(
      "search=react&id=app-1&sort=createdAt&direction=asc",
    );
  });

  it.each(["companyName", "positionTitle", "stage"])(
    "removes invalid legacy sort %s and its direction",
    (sort) => {
      const params = new URLSearchParams(
        `search=react&sort=${sort}&direction=asc&page=3`,
      );

      expect(canonicalizeApplicationsViewParams(params, "board").toString()).toBe(
        "search=react",
      );
    },
  );

  it("removes invalid legacy sorting when canonicalizing list params", () => {
    const params = new URLSearchParams(
      "search=react&sort=stage&direction=asc&stage=interviewing",
    );

    expect(canonicalizeApplicationsViewParams(params, "list").toString()).toBe(
      "search=react&stage=interviewing",
    );
  });

  it("preserves list params while omitting URL defaults", () => {
    const params = new URLSearchParams(
      "search=react&id=app-1&stage=applied&page=2&size=50&sort=updatedAt&direction=desc&view=board",
    );

    expect(canonicalizeApplicationsViewParams(params, "list").toString()).toBe(
      "search=react&id=app-1&stage=applied&page=2&size=50",
    );
  });

  it("keeps a valid direction when sort is absent and removes invalid direction", () => {
    expect(
      canonicalizeApplicationsViewParams(
        new URLSearchParams("direction=asc"),
        "board",
      ).toString(),
    ).toBe("direction=asc");
    expect(
      canonicalizeApplicationsViewParams(
        new URLSearchParams("direction=sideways"),
        "board",
      ).toString(),
    ).toBe("");
  });
});
