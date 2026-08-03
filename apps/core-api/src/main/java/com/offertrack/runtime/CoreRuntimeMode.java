package com.offertrack.runtime;

import java.util.Map;

public enum CoreRuntimeMode {
  SERVER("server"),
  MIGRATE("migrate");

  public static final String ENVIRONMENT_VARIABLE = "OFFERTRACK_RUN_MODE";

  private final String configurationValue;

  CoreRuntimeMode(String configurationValue) {
    this.configurationValue = configurationValue;
  }

  public static CoreRuntimeMode resolve(Map<String, String> environment) {
    if (!environment.containsKey(ENVIRONMENT_VARIABLE)) {
      return SERVER;
    }

    String value = environment.get(ENVIRONMENT_VARIABLE);
    for (CoreRuntimeMode mode : values()) {
      if (mode.configurationValue.equals(value)) {
        return mode;
      }
    }
    throw new CoreStartupException(
        CoreStartupException.Category.INVALID_RUNTIME_MODE, ENVIRONMENT_VARIABLE);
  }
}
