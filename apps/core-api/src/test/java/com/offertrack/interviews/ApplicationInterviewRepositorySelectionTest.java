package com.offertrack.interviews;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationInterviewRepositorySelectionTest {
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-01T12:00:00Z");

  @Test
  void nextInterviewExcludesInitialPassedAndRejected() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();

    ApplicationInterview initial =
        interview(
            applicationId,
            userId,
            InterviewStatus.INITIAL,
            null,
            OffsetDateTime.parse("2026-05-01T10:15:00Z"));
    ApplicationInterview passed =
        interview(
            applicationId,
            userId,
            InterviewStatus.PASSED,
            OffsetDateTime.parse("2026-05-01T11:15:00Z"),
            OffsetDateTime.parse("2026-05-01T09:00:00Z"));

    Map<UUID, ApplicationInterview> nextByApplicationId =
        ApplicationInterviewRepository.selectNextInterviewsByApplicationId(
            List.of(passed, initial), NOW);

    assertThat(nextByApplicationId).doesNotContainKey(applicationId);
  }

  @Test
  void nextInterviewSelectsNearestScheduledNonTerminalInterview() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();

    ApplicationInterview laterScheduled =
        interview(
            applicationId,
            userId,
            InterviewStatus.SCHEDULED,
            OffsetDateTime.parse("2026-05-03T11:15:00Z"),
            OffsetDateTime.parse("2026-05-01T10:15:00Z"));
    ApplicationInterview nearestScheduled =
        interview(
            applicationId,
            userId,
            InterviewStatus.SCHEDULED,
            OffsetDateTime.parse("2026-05-02T11:15:00Z"),
            OffsetDateTime.parse("2026-05-01T11:15:00Z"));
    ApplicationInterview unscheduledInitial =
        interview(
            applicationId,
            userId,
            InterviewStatus.INITIAL,
            null,
            OffsetDateTime.parse("2026-05-01T09:15:00Z"));

    Map<UUID, ApplicationInterview> nextByApplicationId =
        ApplicationInterviewRepository.selectNextInterviewsByApplicationId(
            List.of(laterScheduled, unscheduledInitial, nearestScheduled), NOW);

    assertThat(nextByApplicationId.get(applicationId)).isEqualTo(nearestScheduled);
  }

  @Test
  void nextInterviewDoesNotUseUnscheduledInitial() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();

    ApplicationInterview laterCreatedInitial =
        interview(
            applicationId,
            userId,
            InterviewStatus.INITIAL,
            null,
            OffsetDateTime.parse("2026-05-01T11:15:00Z"));
    ApplicationInterview earlierCreatedInitial =
        interview(
            applicationId,
            userId,
            InterviewStatus.INITIAL,
            null,
            OffsetDateTime.parse("2026-05-01T10:15:00Z"));

    Map<UUID, ApplicationInterview> nextByApplicationId =
        ApplicationInterviewRepository.selectNextInterviewsByApplicationId(
            List.of(laterCreatedInitial, earlierCreatedInitial), NOW);

    assertThat(nextByApplicationId).doesNotContainKey(applicationId);
  }

  @Test
  void nextInterviewIsNullWhenThereIsNoValidCandidate() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();

    ApplicationInterview passed =
        interview(
            applicationId,
            userId,
            InterviewStatus.PASSED,
            OffsetDateTime.parse("2026-05-01T11:15:00Z"),
            OffsetDateTime.parse("2026-05-01T10:15:00Z"));
    ApplicationInterview rejected =
        interview(
            applicationId,
            userId,
            InterviewStatus.REJECTED,
            null,
            OffsetDateTime.parse("2026-05-01T11:45:00Z"));

    Map<UUID, ApplicationInterview> nextByApplicationId =
        ApplicationInterviewRepository.selectNextInterviewsByApplicationId(
            List.of(passed, rejected), NOW);

    assertThat(nextByApplicationId).doesNotContainKey(applicationId);
  }

  private static ApplicationInterview interview(
      UUID applicationId,
      UUID userId,
      InterviewStatus status,
      OffsetDateTime scheduledAt,
      OffsetDateTime createdAt) {
    return new ApplicationInterview(
        UUID.randomUUID(),
        userId,
        applicationId,
        InterviewType.TECHNICAL,
        status,
        scheduledAt,
        createdAt,
        createdAt.plusMinutes(5));
  }
}
