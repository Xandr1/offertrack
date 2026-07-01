package com.offertrack.interviews;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InterviewStatus {
  INITIAL("initial"),
  SCHEDULED("scheduled"),
  PASSED("passed"),
  REJECTED("rejected");

  private final String value;

  InterviewStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static InterviewStatus fromValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Interview status is required");
    }

    for (InterviewStatus status : values()) {
      if (status.value.equalsIgnoreCase(value)) {
        return status;
      }
    }

    throw new IllegalArgumentException("Unknown interview status: " + value);
  }
}
