package com.offertrack.settings;

import com.offertrack.settings.dto.UpdateSettingsRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {
  private final UserSettingsRepository userSettingsRepository;

  public SettingsService(UserSettingsRepository userSettingsRepository) {
    this.userSettingsRepository = userSettingsRepository;
  }

  public UserSettings getSettings(UUID userId) {
    return userSettingsRepository
        .findByUserId(userId)
        .orElseGet(() -> UserSettings.defaultForUser(userId));
  }

  public UserSettings updateSettings(UUID userId, UpdateSettingsRequest request) {
    UserSettings settings =
        new UserSettings(
            userId,
            request.followUpAfterApplyingDays(),
            request.upcomingInterviewDays(),
            request.followUpAfterInterviewDays(),
            normalizeTargetRole(request.targetRole()));

    return userSettingsRepository.upsert(settings);
  }

  private static String normalizeTargetRole(String targetRole) {
    if (targetRole == null) {
      return null;
    }

    String normalized = targetRole.trim();
    return normalized.isBlank() ? null : normalized;
  }
}
