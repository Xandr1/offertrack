package com.offertrack.applications;

public final class AiServiceConfigurationException extends IllegalArgumentException {
  private final String property;

  public AiServiceConfigurationException(String property) {
    super("Invalid AI service configuration for property '" + property + "'");
    this.property = property;
  }

  public String property() {
    return property;
  }
}
