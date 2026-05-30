package com.offertrack.interviews;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InterviewType {
  HR("hr"),
  RECRUITER("recruiter"),
  TECHNICAL("technical"),
  HIRING_MANAGER("hiring_manager"),
  TEAM_MATCH("team_match"),
  HOME_ASSIGNMENT("home_assignment"),
  BEHAVIORAL("behavioral"),
  OTHER("other");

  private final String value;

  InterviewType(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  @JsonCreator
  public static InterviewType fromValue(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Interview type is required");
    }

    for (InterviewType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }

    throw new IllegalArgumentException("Unknown interview type: " + value);
  }
}
