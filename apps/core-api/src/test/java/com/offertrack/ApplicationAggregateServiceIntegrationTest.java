package com.offertrack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.offertrack.applications.ApplicationRepository;
import com.offertrack.applications.ApplicationService;
import com.offertrack.applications.ApplicationStage;
import com.offertrack.applications.DuplicateInterviewIdsException;
import com.offertrack.applications.InvalidInterviewCountException;
import com.offertrack.applications.dto.CreateApplicationInterviewItemRequest;
import com.offertrack.applications.dto.CreateApplicationRequest;
import com.offertrack.applications.dto.ReplaceApplicationInterviewItemRequest;
import com.offertrack.applications.dto.ReplaceApplicationRequest;
import com.offertrack.errors.DomainException;
import com.offertrack.interviews.ApplicationInterviewRepository;
import com.offertrack.interviews.InterviewNotFoundException;
import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import com.offertrack.users.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApplicationAggregateServiceIntegrationTest {
  @Autowired private ApplicationService applicationService;
  @Autowired private ApplicationRepository applicationRepository;
  @Autowired private ApplicationInterviewRepository applicationInterviewRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DSLContext dsl;

  @BeforeEach
  void cleanDatabase() {
    dsl.execute("delete from application_interviews");
    dsl.execute("delete from job_applications");
    dsl.execute("delete from users");
  }

  @Test
  void createApplicationWithoutInterviews() {
    UUID userId = createUser("create-without-interviews@example.com");

    var response =
        applicationService.create(
            userId,
            new CreateApplicationRequest(
                "Acme",
                "Backend Engineer",
                "Warsaw",
                "hybrid",
                ApplicationStage.APPLIED,
                null,
                null,
                null));

    assertThat(response.application().companyName()).isEqualTo("Acme");
    assertThat(response.interviews()).isEmpty();
    assertThat(response.application().nextInterview()).isNull();
  }

  @Test
  void createApplicationWithInterviewsAndDefaultStatus() {
    UUID userId = createUser("create-with-interviews@example.com");

    var response =
        applicationService.create(
            userId,
            new CreateApplicationRequest(
                "Acme",
                "Backend Engineer",
                null,
                null,
                ApplicationStage.APPLIED,
                null,
                null,
                List.of(
                    new CreateApplicationInterviewItemRequest(InterviewType.TECHNICAL, null, null),
                    new CreateApplicationInterviewItemRequest(
                        InterviewType.HR, InterviewStatus.SCHEDULED, null))));

    assertThat(response.interviews()).hasSize(2);
    assertThat(response.interviews().getFirst().status()).isEqualTo(InterviewStatus.PLANNED);
  }

  @Test
  void invalidInterviewRollsBackApplicationCreation() {
    UUID userId = createUser("create-rollback@example.com");

    assertThatThrownBy(
            () ->
                applicationService.create(
                    userId,
                    new CreateApplicationRequest(
                        "Acme",
                        "Backend Engineer",
                        null,
                        null,
                        ApplicationStage.APPLIED,
                        null,
                        null,
                        List.of(
                            new CreateApplicationInterviewItemRequest(
                                InterviewType.TECHNICAL, InterviewStatus.PLANNED, null),
                            new CreateApplicationInterviewItemRequest(
                                null, InterviewStatus.PLANNED, null)))))
        .isInstanceOf(RuntimeException.class);

    assertThat(applicationRepository.listByUser(userId)).isEmpty();
  }

  @Test
  void aggregateReplaceUpdatesApplicationCreatesUpdatesAndDeletesInterviews() {
    UUID userId = createUser("replace-sync@example.com");
    var created =
        applicationService.create(
            userId,
            new CreateApplicationRequest(
                "Acme",
                "Backend Engineer",
                null,
                null,
                ApplicationStage.APPLIED,
                null,
                null,
                List.of(
                    new CreateApplicationInterviewItemRequest(
                        InterviewType.TECHNICAL, InterviewStatus.PLANNED, null),
                    new CreateApplicationInterviewItemRequest(
                        InterviewType.HR, InterviewStatus.SCHEDULED, null))));

    UUID applicationId = created.application().id();
    UUID keepAndUpdateInterviewId = created.interviews().getFirst().id();

    var replaced =
        applicationService.replace(
            userId,
            applicationId,
            new ReplaceApplicationRequest(
                "Updated Acme",
                "Senior Backend Engineer",
                "Remote",
                "remote",
                ApplicationStage.INTERVIEWING,
                "Updated notes",
                null,
                List.of(
                    new ReplaceApplicationInterviewItemRequest(
                        keepAndUpdateInterviewId,
                        InterviewType.TECHNICAL,
                        InterviewStatus.COMPLETED,
                        null),
                    new ReplaceApplicationInterviewItemRequest(
                        null, InterviewType.TEAM_MATCH, InterviewStatus.PLANNED, null))));

    assertThat(replaced.application().companyName()).isEqualTo("Updated Acme");
    assertThat(replaced.interviews()).hasSize(2);
    assertThat(replaced.interviews())
        .anyMatch(
            interview ->
                interview.id().equals(keepAndUpdateInterviewId)
                    && interview.status() == InterviewStatus.COMPLETED);
    assertThat(replaced.interviews())
        .anyMatch(interview -> interview.type() == InterviewType.TEAM_MATCH);
  }

  @Test
  void failedAggregateReplaceRollsBackAllChanges() {
    UUID userId = createUser("replace-rollback@example.com");
    var created =
        applicationService.create(
            userId,
            new CreateApplicationRequest(
                "Acme",
                "Backend Engineer",
                null,
                null,
                ApplicationStage.APPLIED,
                null,
                null,
                List.of(
                    new CreateApplicationInterviewItemRequest(
                        InterviewType.TECHNICAL, InterviewStatus.SCHEDULED, null))));

    UUID applicationId = created.application().id();
    UUID existingInterviewId = created.interviews().getFirst().id();

    assertThatThrownBy(
            () ->
                applicationService.replace(
                    userId,
                    applicationId,
                    new ReplaceApplicationRequest(
                        "Changed Name",
                        "Changed Title",
                        null,
                        null,
                        ApplicationStage.OFFER,
                        null,
                        null,
                        List.of(
                            new ReplaceApplicationInterviewItemRequest(
                                existingInterviewId,
                                InterviewType.TECHNICAL,
                                InterviewStatus.PASSED,
                                null),
                            new ReplaceApplicationInterviewItemRequest(
                                null, null, InterviewStatus.PLANNED, null)))))
        .isInstanceOf(RuntimeException.class);

    var storedApplication =
        applicationRepository.findByIdForUser(applicationId, userId).orElseThrow();
    var storedInterviews =
        applicationInterviewRepository.listByApplicationForUser(applicationId, userId);

    assertThat(storedApplication.companyName()).isEqualTo("Acme");
    assertThat(storedApplication.positionTitle()).isEqualTo("Backend Engineer");
    assertThat(storedApplication.stage()).isEqualTo(ApplicationStage.APPLIED);
    assertThat(storedInterviews).hasSize(1);
    assertThat(storedInterviews.getFirst().status()).isEqualTo(InterviewStatus.SCHEDULED);
  }

  @Test
  void duplicateInterviewIdsReturnBadRequest() {
    UUID userId = createUser("duplicate-ids@example.com");
    var created =
        applicationService.create(
            userId,
            new CreateApplicationRequest(
                "Acme",
                "Backend Engineer",
                null,
                null,
                ApplicationStage.APPLIED,
                null,
                null,
                List.of(
                    new CreateApplicationInterviewItemRequest(
                        InterviewType.TECHNICAL, InterviewStatus.PLANNED, null))));

    UUID applicationId = created.application().id();
    UUID interviewId = created.interviews().getFirst().id();

    assertThatThrownBy(
            () ->
                applicationService.replace(
                    userId,
                    applicationId,
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
                                interviewId,
                                InterviewType.TECHNICAL,
                                InterviewStatus.PLANNED,
                                null),
                            new ReplaceApplicationInterviewItemRequest(
                                interviewId, InterviewType.HR, InterviewStatus.SCHEDULED, null)))))
        .isInstanceOf(DuplicateInterviewIdsException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("DUPLICATE_INTERVIEW_IDS");
  }

  @Test
  void foreignOrWrongParentInterviewIdReturnsNotFound() {
    UUID userId = createUser("wrong-parent@example.com");
    var firstApplication =
        applicationService.create(
            userId,
            new CreateApplicationRequest(
                "Acme 1",
                "Backend Engineer",
                null,
                null,
                ApplicationStage.APPLIED,
                null,
                null,
                List.of(
                    new CreateApplicationInterviewItemRequest(
                        InterviewType.TECHNICAL, InterviewStatus.PLANNED, null))));
    var secondApplication =
        applicationService.create(
            userId,
            new CreateApplicationRequest(
                "Acme 2",
                "Backend Engineer",
                null,
                null,
                ApplicationStage.APPLIED,
                null,
                null,
                List.of(
                    new CreateApplicationInterviewItemRequest(
                        InterviewType.HR, InterviewStatus.PLANNED, null))));

    UUID wrongInterviewId = secondApplication.interviews().getFirst().id();

    assertThatThrownBy(
            () ->
                applicationService.replace(
                    userId,
                    firstApplication.application().id(),
                    new ReplaceApplicationRequest(
                        "Acme 1",
                        "Backend Engineer",
                        null,
                        null,
                        ApplicationStage.APPLIED,
                        null,
                        null,
                        List.of(
                            new ReplaceApplicationInterviewItemRequest(
                                wrongInterviewId,
                                InterviewType.HR,
                                InterviewStatus.SCHEDULED,
                                null)))))
        .isInstanceOf(InterviewNotFoundException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("INTERVIEW_NOT_FOUND");
  }

  @Test
  void maxTenInterviewsIsEnforced() {
    UUID userId = createUser("max-ten@example.com");
    var created =
        applicationService.create(
            userId,
            new CreateApplicationRequest(
                "Acme",
                "Backend Engineer",
                null,
                null,
                ApplicationStage.APPLIED,
                null,
                null,
                null));

    List<ReplaceApplicationInterviewItemRequest> interviews =
        java.util.stream.IntStream.range(0, 11)
            .mapToObj(
                index ->
                    new ReplaceApplicationInterviewItemRequest(
                        null, InterviewType.TECHNICAL, InterviewStatus.PLANNED, null))
            .toList();

    assertThatThrownBy(
            () ->
                applicationService.replace(
                    userId,
                    created.application().id(),
                    new ReplaceApplicationRequest(
                        "Acme",
                        "Backend Engineer",
                        null,
                        null,
                        ApplicationStage.APPLIED,
                        null,
                        null,
                        interviews)))
        .isInstanceOf(InvalidInterviewCountException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("INVALID_INTERVIEW_COUNT");
  }

  @Test
  void nextInterviewIsRecomputedAfterCreateAndReplaceSync() {
    UUID userId = createUser("next-interview@example.com");
    OffsetDateTime later = OffsetDateTime.parse("2026-06-01T10:00:00Z");
    OffsetDateTime sooner = OffsetDateTime.parse("2026-05-20T10:00:00Z");

    var created =
        applicationService.create(
            userId,
            new CreateApplicationRequest(
                "Acme",
                "Backend Engineer",
                null,
                null,
                ApplicationStage.APPLIED,
                null,
                null,
                List.of(
                    new CreateApplicationInterviewItemRequest(
                        InterviewType.TECHNICAL, InterviewStatus.SCHEDULED, later),
                    new CreateApplicationInterviewItemRequest(
                        InterviewType.HR, InterviewStatus.PASSED, sooner))));

    assertThat(created.application().nextInterview()).isNotNull();
    assertThat(created.application().nextInterview().type()).isEqualTo(InterviewType.TECHNICAL);

    UUID applicationId = created.application().id();
    UUID technicalInterviewId =
        created.interviews().stream()
            .filter(interview -> interview.type() == InterviewType.TECHNICAL)
            .findFirst()
            .orElseThrow()
            .id();

    var replaced =
        applicationService.replace(
            userId,
            applicationId,
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
                        technicalInterviewId,
                        InterviewType.TECHNICAL,
                        InterviewStatus.PASSED,
                        later),
                    new ReplaceApplicationInterviewItemRequest(
                        null, InterviewType.TEAM_MATCH, InterviewStatus.PLANNED, sooner))));

    assertThat(replaced.application().nextInterview()).isNotNull();
    assertThat(replaced.application().nextInterview().type()).isEqualTo(InterviewType.TEAM_MATCH);
  }

  private UUID createUser(String email) {
    return userRepository.createUser(email, "hash", "Test User").id();
  }
}
