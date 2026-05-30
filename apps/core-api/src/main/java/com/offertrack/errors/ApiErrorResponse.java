package com.offertrack.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    int status,
    String code,
    String message,
    String path,
    OffsetDateTime timestamp,
    List<FieldError> fieldErrors) {
  public static ApiErrorResponse of(int status, String code, String message, String path) {
    return new ApiErrorResponse(
        status, code, message, path, OffsetDateTime.now(ZoneOffset.UTC), null);
  }

  public static ApiErrorResponse withFieldErrors(
      int status, String code, String message, String path, List<FieldError> fieldErrors) {
    return new ApiErrorResponse(
        status, code, message, path, OffsetDateTime.now(ZoneOffset.UTC), fieldErrors);
  }

  public record FieldError(String field, String message) {}
}
