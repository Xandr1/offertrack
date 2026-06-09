package com.offertrack.settings;

import java.util.UUID;

public record UserSettings(
    UUID userId,
    int followUpAfterApplyingDays,
    int upcomingInterviewDays,
    int followUpAfterInterviewDays,
    String targetRole) {
  public static final int DEFAULT_FOLLOW_UP_AFTER_APPLYING_DAYS = 7;
  public static final int DEFAULT_UPCOMING_INTERVIEW_DAYS = 7;
  public static final int DEFAULT_FOLLOW_UP_AFTER_INTERVIEW_DAYS = 2;

  public static UserSettings defaultForUser(UUID userId) {
    return new UserSettings(
        userId,
        DEFAULT_FOLLOW_UP_AFTER_APPLYING_DAYS,
        DEFAULT_UPCOMING_INTERVIEW_DAYS,
        DEFAULT_FOLLOW_UP_AFTER_INTERVIEW_DAYS,
        null);
  }
}
