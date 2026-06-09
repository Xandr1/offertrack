package com.offertrack.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSettingsRequest(
    @NotNull(message = "Follow up after applying days is required")
        @Min(value = 1, message = "Follow up after applying days must be at least 1")
        @Max(value = 60, message = "Follow up after applying days must be at most 60")
        Integer followUpAfterApplyingDays,
    @NotNull(message = "Upcoming interview days is required")
        @Min(value = 1, message = "Upcoming interview days must be at least 1")
        @Max(value = 60, message = "Upcoming interview days must be at most 60")
        Integer upcomingInterviewDays,
    @NotNull(message = "Follow up after interview days is required")
        @Min(value = 1, message = "Follow up after interview days must be at least 1")
        @Max(value = 30, message = "Follow up after interview days must be at most 30")
        Integer followUpAfterInterviewDays,
    @Size(max = 160, message = "Target role must be at most 160 characters") String targetRole) {
  public UpdateSettingsRequest {
    targetRole = normalizeTargetRole(targetRole);
  }

  private static String normalizeTargetRole(String targetRole) {
    if (targetRole == null) {
      return null;
    }

    String normalized = targetRole.trim();
    return normalized.isBlank() ? null : normalized;
  }
}
