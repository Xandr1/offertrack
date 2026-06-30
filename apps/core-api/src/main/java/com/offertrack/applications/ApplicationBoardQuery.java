package com.offertrack.applications;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record ApplicationBoardQuery(
    String search,
    ApplicationStage stageFilter,
    int offset,
    ApplicationListQuery.ApplicationSort sort,
    ApplicationListQuery.SortDirection direction) {
  public ApplicationBoardQuery(
      String search,
      int offset,
      ApplicationListQuery.ApplicationSort sort,
      ApplicationListQuery.SortDirection direction) {
    this(search, null, offset, sort, direction);
  }

  public static ApplicationBoardQuery initial(String search) {
    return initial(search, null, null, null);
  }

  public static ApplicationBoardQuery initial(String search, String sort, String direction) {
    return initial(search, null, sort, direction);
  }

  public static ApplicationBoardQuery initial(
      String search, String stage, String sort, String direction) {
    return new ApplicationBoardQuery(
        normalizeSearch(search),
        parseOptionalStage(stage),
        0,
        ApplicationListQuery.parseSort(sort),
        ApplicationListQuery.parseDirection(direction));
  }

  public static ApplicationBoardQuery column(String search, Integer offset) {
    return column(search, null, offset, null, null);
  }

  public static ApplicationBoardQuery column(
      String search, Integer offset, String sort, String direction) {
    return column(search, null, offset, sort, direction);
  }

  public static ApplicationBoardQuery column(
      String search, String stage, Integer offset, String sort, String direction) {
    int parsedOffset = offset == null ? 0 : offset;
    if (parsedOffset < 0) {
      throw invalidRequest("Offset must be greater than or equal to 0.");
    }

    return new ApplicationBoardQuery(
        normalizeSearch(search),
        parseOptionalStage(stage),
        parsedOffset,
        ApplicationListQuery.parseSort(sort),
        ApplicationListQuery.parseDirection(direction));
  }

  public static ApplicationStage parseStage(String value) {
    try {
      return ApplicationStage.fromValue(value);
    } catch (IllegalArgumentException exception) {
      throw invalidRequest("Invalid stage parameter.");
    }
  }

  private static ApplicationStage parseOptionalStage(String value) {
    return value == null ? null : parseStage(value);
  }

  private static String normalizeSearch(String value) {
    if (value == null) {
      return null;
    }

    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static ResponseStatusException invalidRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
