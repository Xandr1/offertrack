package com.offertrack.applications;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record ApplicationListQuery(
    int page,
    int size,
    String search,
    ApplicationStage stage,
    ApplicationSort sort,
    SortDirection direction) {
  public static final int DEFAULT_PAGE = 0;
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;
  public static final ApplicationSort DEFAULT_SORT = ApplicationSort.UPDATED_AT;
  public static final SortDirection DEFAULT_DIRECTION = SortDirection.DESC;

  public static ApplicationListQuery defaults() {
    return new ApplicationListQuery(
        DEFAULT_PAGE, DEFAULT_SIZE, null, null, DEFAULT_SORT, DEFAULT_DIRECTION);
  }

  public static ApplicationListQuery fromRequestParams(
      Integer page, Integer size, String search, String stage, String sort, String direction) {
    int parsedPage = page == null ? DEFAULT_PAGE : page;
    int parsedSize = size == null ? DEFAULT_SIZE : size;

    if (parsedPage < 0) {
      throw invalidRequest("Page must be greater than or equal to 0.");
    }

    if (parsedSize < 1 || parsedSize > MAX_SIZE) {
      throw invalidRequest("Size must be between 1 and 100.");
    }

    return new ApplicationListQuery(
        parsedPage,
        parsedSize,
        normalizeSearch(search),
        parseStage(stage),
        parseSort(sort),
        parseDirection(direction));
  }

  public long offset() {
    return (long) page * size;
  }

  private static String normalizeSearch(String value) {
    if (value == null) {
      return null;
    }

    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static ApplicationStage parseStage(String value) {
    if (value == null) {
      return null;
    }

    try {
      return ApplicationStage.fromValue(value);
    } catch (IllegalArgumentException exception) {
      throw invalidRequest("Invalid stage parameter.");
    }
  }

  static ApplicationSort parseSort(String value) {
    if (value == null) {
      return DEFAULT_SORT;
    }

    for (ApplicationSort sort : ApplicationSort.values()) {
      if (sort.value().equals(value)) {
        return sort;
      }
    }

    throw invalidRequest("Invalid sort parameter.");
  }

  static SortDirection parseDirection(String value) {
    if (value == null) {
      return DEFAULT_DIRECTION;
    }

    for (SortDirection direction : SortDirection.values()) {
      if (direction.value().equals(value)) {
        return direction;
      }
    }

    throw invalidRequest("Invalid direction parameter.");
  }

  private static ResponseStatusException invalidRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  public enum ApplicationSort {
    UPDATED_AT("updatedAt"),
    CREATED_AT("createdAt");

    private final String value;

    ApplicationSort(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }

  public enum SortDirection {
    ASC("asc"),
    DESC("desc");

    private final String value;

    SortDirection(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }
}
