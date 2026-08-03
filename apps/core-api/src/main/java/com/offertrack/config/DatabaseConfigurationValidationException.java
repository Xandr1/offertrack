package com.offertrack.config;

public final class DatabaseConfigurationValidationException extends IllegalArgumentException {
  private final String property;

  DatabaseConfigurationValidationException(String property) {
    super("Invalid database configuration for property '" + property + "'");
    this.property = property;
  }

  public String property() {
    return property;
  }
}
