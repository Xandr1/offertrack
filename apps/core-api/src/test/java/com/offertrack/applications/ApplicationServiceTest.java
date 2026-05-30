package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offertrack.applications.dto.CreateApplicationInterviewItemRequest;
import com.offertrack.applications.dto.CreateApplicationRequest;
import com.offertrack.applications.dto.ReplaceApplicationInterviewItemRequest;
import com.offertrack.applications.dto.ReplaceApplicationRequest;
import com.offertrack.applications.dto.UpdateApplicationStageRequest;
import com.offertrack.errors.DomainException;
import com.offertrack.interviews.ApplicationInterview;
import com.offertrack.interviews.ApplicationInterviewRepository;
import com.offertrack.interviews.InterviewNotFoundException;
import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {
  @Mock private ApplicationRepository applicationRepository;
  @Mock private ApplicationInterviewRepository applicationInterviewRepository;

  @InjectMocks private ApplicationService applicationService;

  @Test
  void updateStageReturnsNotFoundWhenApplicationIsMissing() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UpdateApplicationStageRequest request =
        new UpdateApplicationStageRequest(ApplicationStage.APPLIED);

    when(applicationRepository.updateStage(applicationId, userId, ApplicationStage.APPLIED))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> applicationService.updateStage(userId, applicationId, request))
        .isInstanceOf(ApplicationNotFoundException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("APPLICATION_NOT_FOUND");
  }

  @Test
  void replaceReturnsNotFoundWhenApplicationIsMissing() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ReplaceApplicationRequest request =
        new ReplaceApplicationRequest(
            "Acme",
            "Backend Engineer",
            null,
            null,
            ApplicationStage.APPLIED,
            null,
            null,
            List.of());

    when(applicationRepository.findByIdForUser(applicationId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> applicationService.replace(userId, applicationId, request))
        .isInstanceOf(ApplicationNotFoundException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("APPLICATION_NOT_FOUND");
  }

  @Test
  void replaceReturnsBadRequestWhenDuplicateInterviewIdsProvided() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();
    ReplaceApplicationRequest request =
        new ReplaceApplicationRequest(
            "Acme",
            "Backend Engineer",
            null,
            null,
            ApplicationStage.APPLIED,
            null,
            null,
            List.of(
                new ReplaceApplicationInterviewItemRequest(
                    interviewId, InterviewType.TECHNICAL, InterviewStatus.SCHEDULED, null),
                new ReplaceApplicationInterviewItemRequest(
                    interviewId, InterviewType.HR, InterviewStatus.PLANNED, null)));

    when(applicationRepository.findByIdForUser(applicationId, userId))
        .thenReturn(Optional.of(sampleApplication(applicationId, userId)));
    when(applicationInterviewRepository.listByApplicationForUser(applicationId, userId))
        .thenReturn(
            List.of(
                sampleInterview(
                    interviewId,
                    applicationId,
                    userId,
                    InterviewType.TECHNICAL,
                    InterviewStatus.SCHEDULED)));

    assertThatThrownBy(() -> applicationService.replace(userId, applicationId, request))
        .isInstanceOf(DuplicateInterviewIdsException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("DUPLICATE_INTERVIEW_IDS");
  }

  @Test
  void replaceReturnsNotFoundWhenInterviewIdIsUnknownOrForeign() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ReplaceApplicationRequest request =
        new ReplaceApplicationRequest(
            "Acme",
            "Backend Engineer",
            null,
            null,
            ApplicationStage.APPLIED,
            null,
            null,
            List.of(
                new ReplaceApplicationInterviewItemRequest(
                    UUID.randomUUID(), InterviewType.TECHNICAL, InterviewStatus.SCHEDULED, null)));

    when(applicationRepository.findByIdForUser(applicationId, userId))
        .thenReturn(Optional.of(sampleApplication(applicationId, userId)));
    when(applicationInterviewRepository.listByApplicationForUser(applicationId, userId))
        .thenReturn(List.of());

    assertThatThrownBy(() -> applicationService.replace(userId, applicationId, request))
        .isInstanceOf(InterviewNotFoundException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("INTERVIEW_NOT_FOUND");
  }

  @Test
  void replaceReturnsBadRequestWhenInterviewLimitExceeded() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    List<ReplaceApplicationInterviewItemRequest> interviews =
        java.util.stream.IntStream.range(0, 11)
            .mapToObj(
                index ->
                    new ReplaceApplicationInterviewItemRequest(
                        null, InterviewType.TECHNICAL, InterviewStatus.PLANNED, null))
            .toList();
    ReplaceApplicationRequest request =
        new ReplaceApplicationRequest(
            "Acme",
            "Backend Engineer",
            null,
            null,
            ApplicationStage.APPLIED,
            null,
            null,
            interviews);

    when(applicationRepository.findByIdForUser(applicationId, userId))
        .thenReturn(Optional.of(sampleApplication(applicationId, userId)));

    assertThatThrownBy(() -> applicationService.replace(userId, applicationId, request))
        .isInstanceOf(InvalidInterviewCountException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("INVALID_INTERVIEW_COUNT");
  }

  @Test
  void deleteReturnsNotFoundWhenApplicationIsMissing() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();

    when(applicationRepository.delete(applicationId, userId)).thenReturn(false);

    assertThatThrownBy(() -> applicationService.delete(userId, applicationId))
        .isInstanceOf(ApplicationNotFoundException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("APPLICATION_NOT_FOUND");
  }

  @Test
  void listUsesBatchLookupForNextInterview() {
    UUID userId = UUID.randomUUID();
    Application first = sampleApplication(UUID.randomUUID(), userId);
    Application second = sampleApplication(UUID.randomUUID(), userId);
    ApplicationInterview nextInterview =
        sampleInterview(
            UUID.randomUUID(),
            first.id(),
            userId,
            InterviewType.TECHNICAL,
            InterviewStatus.SCHEDULED);

    when(applicationRepository.listByUser(userId)).thenReturn(List.of(first, second));
    when(applicationInterviewRepository.findNextByApplicationIdsForUser(
            userId, List.of(first.id(), second.id())))
        .thenReturn(Map.of(first.id(), nextInterview));

    var response = applicationService.list(userId);

    assertThat(response).hasSize(2);
    assertThat(response.getFirst().nextInterview()).isNotNull();
    assertThat(response.getFirst().nextInterview().id()).isEqualTo(nextInterview.id());
    assertThat(response.get(1).nextInterview()).isNull();
    verify(applicationInterviewRepository)
        .findNextByApplicationIdsForUser(userId, List.of(first.id(), second.id()));
    verify(applicationInterviewRepository, never())
        .findNextByApplicationForUser(first.id(), userId);
  }

  @Test
  void createDefaultsInterviewStatusToPlannedWhenMissing() {
    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    Application application = sampleApplication(applicationId, userId);
    CreateApplicationRequest request =
        new CreateApplicationRequest(
            "Acme",
            "Backend Engineer",
            null,
            null,
            ApplicationStage.APPLIED,
            null,
            null,
            List.of(
                new CreateApplicationInterviewItemRequest(InterviewType.TECHNICAL, null, null)));

    when(applicationRepository.create(userId, request, ApplicationStage.APPLIED))
        .thenReturn(application);
    when(applicationInterviewRepository.listByApplicationForUser(applicationId, userId))
        .thenReturn(List.of());
    when(applicationInterviewRepository.findNextByApplicationForUser(applicationId, userId))
        .thenReturn(Optional.empty());

    applicationService.create(userId, request);

    verify(applicationInterviewRepository)
        .create(applicationId, userId, InterviewType.TECHNICAL, InterviewStatus.PLANNED, null);
  }

  private static Application sampleApplication(UUID applicationId, UUID userId) {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-01T10:15:00Z");

    return new Application(
        applicationId,
        userId,
        "Acme",
        "Backend Engineer",
        "Warsaw",
        "hybrid",
        ApplicationStage.APPLIED,
        null,
        now,
        now,
        now);
  }

  private static ApplicationInterview sampleInterview(
      UUID interviewId,
      UUID applicationId,
      UUID userId,
      InterviewType type,
      InterviewStatus status) {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-01T10:15:00Z");

    return new ApplicationInterview(
        interviewId, userId, applicationId, type, status, now.plusDays(1), now, now);
  }
}
