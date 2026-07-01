package com.offertrack.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offertrack.applications.ApplicationNotFoundException;
import com.offertrack.applications.ApplicationRepository;
import com.offertrack.errors.DomainException;
import com.offertrack.interviews.dto.UpdateInterviewStatusRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationInterviewServiceTest {
  @Mock private ApplicationInterviewRepository applicationInterviewRepository;
  @Mock private ApplicationRepository applicationRepository;

  private ApplicationInterviewService applicationInterviewService;

  @BeforeEach
  void setUp() {
    applicationInterviewService =
        new ApplicationInterviewService(
            applicationInterviewRepository,
            applicationRepository,
            Clock.fixed(java.time.Instant.parse("2026-05-01T10:15:00Z"), java.time.ZoneOffset.UTC));
  }

  @Test
  void listReturnsNotFoundWhenApplicationIsMissingOrForeign() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();

    when(applicationRepository.findByIdForUser(applicationId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> applicationInterviewService.list(userId, applicationId))
        .isInstanceOf(ApplicationNotFoundException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("APPLICATION_NOT_FOUND");
  }

  @Test
  void patchStatusWorks() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();
    UpdateInterviewStatusRequest request = new UpdateInterviewStatusRequest(InterviewStatus.PASSED);

    when(applicationInterviewRepository.updateStatus(
            applicationId, interviewId, userId, InterviewStatus.PASSED))
        .thenReturn(
            Optional.of(
                sampleInterview(
                    userId,
                    applicationId,
                    InterviewType.TECHNICAL,
                    InterviewStatus.PASSED,
                    OffsetDateTime.parse("2026-05-05T09:00:00Z"))));

    var response =
        applicationInterviewService.updateStatus(userId, applicationId, interviewId, request);

    assertThat(response.status()).isEqualTo(InterviewStatus.PASSED);
  }

  @Test
  void patchStatusReturnsNotFoundWhenInterviewIsMissingForeignOrWrongParent() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();
    UpdateInterviewStatusRequest request =
        new UpdateInterviewStatusRequest(InterviewStatus.REJECTED);

    when(applicationInterviewRepository.updateStatus(
            applicationId, interviewId, userId, InterviewStatus.REJECTED))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                applicationInterviewService.updateStatus(
                    userId, applicationId, interviewId, request))
        .isInstanceOf(InterviewNotFoundException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("INTERVIEW_NOT_FOUND");
  }

  @Test
  void markFollowedUpUsesInjectedClockAndReturnsUpdatedTimestamp() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();
    OffsetDateTime followedUpAt = OffsetDateTime.parse("2026-05-01T10:15:00Z");
    ApplicationInterview interview =
        new ApplicationInterview(
            interviewId,
            userId,
            applicationId,
            InterviewType.TECHNICAL,
            InterviewStatus.SCHEDULED,
            followedUpAt.minusDays(3),
            followedUpAt,
            followedUpAt.minusDays(10),
            followedUpAt);

    when(applicationInterviewRepository.markFollowedUp(
            applicationId, interviewId, userId, followedUpAt))
        .thenReturn(Optional.of(interview));

    var response = applicationInterviewService.markFollowedUp(userId, applicationId, interviewId);

    assertThat(response.followedUpAt()).isEqualTo(followedUpAt);
    verify(applicationInterviewRepository)
        .markFollowedUp(applicationId, interviewId, userId, followedUpAt);
  }

  @Test
  void markFollowedUpReturnsNotFoundForMissingForeignOrWrongParentInterview() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();
    OffsetDateTime followedUpAt = OffsetDateTime.parse("2026-05-01T10:15:00Z");

    when(applicationInterviewRepository.markFollowedUp(
            applicationId, interviewId, userId, followedUpAt))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> applicationInterviewService.markFollowedUp(userId, applicationId, interviewId))
        .isInstanceOf(InterviewNotFoundException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("INTERVIEW_NOT_FOUND");
  }

  private static ApplicationInterview sampleInterview(
      UUID userId,
      UUID applicationId,
      InterviewType type,
      InterviewStatus status,
      OffsetDateTime scheduledAt) {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-01T10:15:00Z");

    return new ApplicationInterview(
        UUID.randomUUID(), userId, applicationId, type, status, scheduledAt, now, now);
  }
}
