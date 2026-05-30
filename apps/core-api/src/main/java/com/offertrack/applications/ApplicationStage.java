package com.offertrack.applications;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ApplicationStage {
  INITIAL("initial"),
  APPLIED("applied"),
  INTERVIEWING("interviewing"),
  REJECTED("rejected"),
  OFFER("offer");

  private final String value;

  ApplicationStage(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static ApplicationStage fromValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Application stage is required");
    }

    for (ApplicationStage stage : values()) {
      if (stage.value.equalsIgnoreCase(value)) {
        return stage;
      }
    }

    throw new IllegalArgumentException("Unknown application stage: " + value);
  }
}
