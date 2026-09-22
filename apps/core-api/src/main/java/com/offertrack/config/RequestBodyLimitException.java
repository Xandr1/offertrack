package com.offertrack.config;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class RequestBodyLimitException extends RuntimeException {
  public RequestBodyLimitException() {
    super("Request body exceeds the configured size limit.");
  }

  public static RequestBodyLimitException find(Throwable exception) {
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable cause = exception;
        cause != null && visited.add(cause);
        cause = cause.getCause()) {
      if (cause instanceof RequestBodyLimitException limit) {
        return limit;
      }
    }
    return null;
  }

  public static void rethrowIfPresent(Throwable exception) {
    RequestBodyLimitException limit = find(exception);
    if (limit != null) {
      throw limit;
    }
  }
}
