package com.offertrack.applications;

public enum AiServiceAuthMode {
  INTERNAL_KEY("internal-key"),
  GOOGLE_ID_TOKEN("google-id-token");

  private final String configurationValue;

  AiServiceAuthMode(String configurationValue) {
    this.configurationValue = configurationValue;
  }

  public String configurationValue() {
    return configurationValue;
  }

  public static AiServiceAuthMode fromConfiguration(String value) {
    if (value == null) {
      return INTERNAL_KEY;
    }
    for (AiServiceAuthMode mode : values()) {
      if (mode.configurationValue.equals(value)) {
        return mode;
      }
    }
    throw new AiServiceConfigurationException("app.ai-service.auth-mode");
  }
}
