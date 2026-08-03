package com.offertrack.runtime;

public final class CoreStartupException extends RuntimeException {
  private final Category category;
  private final String property;

  CoreStartupException(Category category, String property) {
    super("Core startup failed for category " + category + " and property " + property);
    this.category = category;
    this.property = property;
  }

  public Category category() {
    return category;
  }

  public String property() {
    return property;
  }

  public enum Category {
    INVALID_RUNTIME_MODE,
    INVALID_DATABASE_CONFIGURATION,
    UNSUPPORTED_PROFILE_SOURCE
  }
}
