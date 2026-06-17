import {
  parseDirectionParam,
  parsePageParam,
  parseSearchQueryParam,
  parseSizeParam,
  parseSortParam,
  parseStageFilterParam,
  writeApplicationsListParams,
} from "./application-filters";

describe("application-filters", () => {
  it("parses list query params with defaults", () => {
    expect(parseStageFilterParam("offer")).toBe("offer");
    expect(parseStageFilterParam("invalid")).toBe("all");
    expect(parseSortParam("companyName")).toBe("companyName");
    expect(parseSortParam("invalid")).toBe("updatedAt");
    expect(parseDirectionParam("asc")).toBe("asc");
    expect(parseDirectionParam("invalid")).toBe("desc");
    expect(parsePageParam("2")).toBe(2);
    expect(parsePageParam("-1")).toBe(0);
    expect(parseSizeParam("50")).toBe(50);
    expect(parseSizeParam("101")).toBe(20);
    expect(parseSearchQueryParam("  acme  ")).toBe("acme");
    expect(parseSearchQueryParam(null)).toBe("");
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
      sort: "companyName",
      stage: "applied",
    });

    expect(params.toString()).toBe(
      "id=app-1&page=2&size=50&search=acme&stage=applied&sort=companyName&direction=asc",
    );
  });
});
