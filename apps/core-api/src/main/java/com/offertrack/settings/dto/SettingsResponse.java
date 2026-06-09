package com.offertrack.settings.dto;

import com.offertrack.settings.UserSettings;

public record SettingsResponse(
    int followUpAfterApplyingDays,
    int upcomingInterviewDays,
    int followUpAfterInterviewDays,
    String targetRole) {
  public static SettingsResponse from(UserSettings settings) {
    return new SettingsResponse(
        settings.followUpAfterApplyingDays(),
        settings.upcomingInterviewDays(),
        settings.followUpAfterInterviewDays(),
        settings.targetRole());
  }
}
