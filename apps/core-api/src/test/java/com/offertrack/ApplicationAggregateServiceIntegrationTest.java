package com.offertrack;

import static com.offertrack.jooq.generated.tables.JobApplications.JOB_APPLICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.offertrack.applications.ApplicationBoardQuery;
import com.offertrack.applications.ApplicationListQuery;
import com.offertrack.applications.ApplicationNotFoundException;
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
import java.util.Comparator;
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
                null,
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
  void createListAndReplaceApplicationJobUrl() {
    UUID userId = createUser("job-url@example.com");

    var created =
        applicationService.create(
            userId,
            new CreateApplicationRequest(
                "Acme",
                "Backend Engineer",
                "  google.com/careers/backend-engineer  ",
                null,
                null,
                ApplicationStage.APPLIED,
                null,
                null,
                null));

    UUID applicationId = created.application().id();

    assertThat(created.application().jobUrl())
        .isEqualTo("https://google.com/careers/backend-engineer");
    assertThat(applicationRepository.listByUser(userId).getFirst().jobUrl())
        .isEqualTo("https://google.com/careers/backend-engineer");

    var replaced =
        applicationService.replace(
            userId,
            applicationId,
            new ReplaceApplicationRequest(
                "Acme",
                "Backend Engineer",
                "  ",
                null,
                null,
                ApplicationStage.APPLIED,
                null,
                null,
                List.of()));

    assertThat(replaced.application().jobUrl()).isNull();
    assertThat(applicationRepository.findByIdForUser(applicationId, userId).orElseThrow().jobUrl())
        .isNull();
  }

  @Test
  void listUsesDefaultPaginationMetadata() {
    UUID userId = createUser("list-defaults@example.com");
    createApplication(userId, "Acme", "Backend Engineer", ApplicationStage.APPLIED);
    createApplication(userId, "Globex", "Frontend Engineer", ApplicationStage.INTERVIEWING);

    var response = applicationService.list(userId);

    assertThat(response.items()).hasSize(2);
    assertThat(response.page()).isZero();
    assertThat(response.size()).isEqualTo(20);
    assertThat(response.totalItems()).isEqualTo(2);
    assertThat(response.totalPages()).isEqualTo(1);
  }

  @Test
  void listReturnsPageSizeMetadata() {
    UUID userId = createUser("list-page-size@example.com");
    createApplication(userId, "Acme", "Backend Engineer", ApplicationStage.APPLIED);
    createApplication(userId, "Globex", "Frontend Engineer", ApplicationStage.INTERVIEWING);
    createApplication(userId, "Initech", "Platform Engineer", ApplicationStage.OFFER);

    var response =
        applicationService.list(
            userId, ApplicationListQuery.fromRequestParams(1, 2, null, null, null, null));

    assertThat(response.items()).hasSize(1);
    assertThat(response.page()).isEqualTo(1);
    assertThat(response.size()).isEqualTo(2);
    assertThat(response.totalItems()).isEqualTo(3);
    assertThat(response.totalPages()).isEqualTo(2);
  }

  @Test
  void listReturnsEmptyItemsForBeyondRangePage() {
    UUID userId = createUser("list-beyond-range@example.com");
    createApplication(userId, "Acme", "Backend Engineer", ApplicationStage.APPLIED);

    var response =
        applicationService.list(
            userId, ApplicationListQuery.fromRequestParams(3, 1, null, null, null, null));

    assertThat(response.items()).isEmpty();
    assertThat(response.page()).isEqualTo(3);
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.totalItems()).isEqualTo(1);
    assertThat(response.totalPages()).isEqualTo(1);
  }

  @Test
  void listReturnsZeroMetadataForEmptyResults() {
    UUID userId = createUser("list-empty@example.com");

    var response = applicationService.list(userId);

    assertThat(response.items()).isEmpty();
    assertThat(response.totalItems()).isZero();
    assertThat(response.totalPages()).isZero();
  }

  @Test
  void listSearchesCompanyNameCaseInsensitively() {
    UUID userId = createUser("list-search-company@example.com");
    createApplication(userId, "Acme", "Backend Engineer", ApplicationStage.APPLIED);
    createApplication(userId, "Globex", "Frontend Engineer", ApplicationStage.INTERVIEWING);

    var response =
        applicationService.list(
            userId, ApplicationListQuery.fromRequestParams(0, 20, "  acM  ", null, null, null));

    assertThat(response.items()).extracting("companyName").containsExactly("Acme");
  }

  @Test
  void listSearchesPositionTitleCaseInsensitively() {
    UUID userId = createUser("list-search-title@example.com");
    createApplication(userId, "Acme", "Backend Engineer", ApplicationStage.APPLIED);
    createApplication(userId, "Globex", "Frontend Engineer", ApplicationStage.INTERVIEWING);

    var response =
        applicationService.list(
            userId, ApplicationListQuery.fromRequestParams(0, 20, "front", null, null, null));

    assertThat(response.items()).extracting("positionTitle").containsExactly("Frontend Engineer");
  }

  @Test
  void listSearchDoesNotMatchUnrelatedFields() {
    UUID userId = createUser("list-search-unrelated@example.com");
    applicationService.create(
        userId,
        new CreateApplicationRequest(
            "Acme",
            "Backend Engineer",
            "https://needle.example.com/jobs/1",
            "Needle City",
            null,
            ApplicationStage.APPLIED,
            "needle notes",
            null,
            null));

    var response =
        applicationService.list(
            userId, ApplicationListQuery.fromRequestParams(0, 20, "needle", null, null, null));

    assertThat(response.items()).isEmpty();
    assertThat(response.totalItems()).isZero();
    assertThat(response.totalPages()).isZero();
  }

  @Test
  void listFiltersByStage() {
    UUID userId = createUser("list-stage@example.com");
    createApplication(userId, "Acme", "Backend Engineer", ApplicationStage.APPLIED);
    createApplication(userId, "Globex", "Frontend Engineer", ApplicationStage.OFFER);

    var response =
        applicationService.list(
            userId, ApplicationListQuery.fromRequestParams(0, 20, null, "offer", null, null));

    assertThat(response.items()).extracting("stage").containsExactly(ApplicationStage.OFFER);
  }

  @Test
  void listSupportsAllowedSorting() {
    UUID userId = createUser("list-sort@example.com");
    UUID firstId = createApplication(userId, "Beta", "Analyst", ApplicationStage.APPLIED);
    UUID secondId = createApplication(userId, "Acme", "Engineer", ApplicationStage.INTERVIEWING);
    OffsetDateTime older = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    OffsetDateTime newer = OffsetDateTime.parse("2026-02-01T00:00:00Z");
    dsl.update(JOB_APPLICATIONS)
        .set(JOB_APPLICATIONS.CREATED_AT, older)
        .set(JOB_APPLICATIONS.UPDATED_AT, newer)
        .where(JOB_APPLICATIONS.ID.eq(firstId))
        .execute();
    dsl.update(JOB_APPLICATIONS)
        .set(JOB_APPLICATIONS.CREATED_AT, newer)
        .set(JOB_APPLICATIONS.UPDATED_AT, older)
        .where(JOB_APPLICATIONS.ID.eq(secondId))
        .execute();

    var createdAsc =
        applicationService.list(
            userId, ApplicationListQuery.fromRequestParams(0, 20, null, null, "createdAt", "asc"));
    var updatedDesc =
        applicationService.list(
            userId, ApplicationListQuery.fromRequestParams(0, 20, null, null, "updatedAt", "desc"));

    assertThat(createdAsc.items()).extracting("id").containsExactly(firstId, secondId);
    assertThat(updatedDesc.items()).extracting("id").containsExactly(firstId, secondId);
  }

  @Test
  void listUsesIdAsSecondarySortForTiedPrimaryValues() {
    UUID userId = createUser("list-sort-tie@example.com");
    UUID firstId = createApplication(userId, "Acme", "Backend Engineer", ApplicationStage.APPLIED);
    UUID secondId =
        createApplication(userId, "Acme", "Frontend Engineer", ApplicationStage.INTERVIEWING);
    UUID thirdId = createApplication(userId, "Acme", "Platform Engineer", ApplicationStage.OFFER);
    OffsetDateTime tiedCreatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    dsl.update(JOB_APPLICATIONS)
        .set(JOB_APPLICATIONS.CREATED_AT, tiedCreatedAt)
        .where(JOB_APPLICATIONS.USER_ID.eq(userId))
        .execute();
    List<UUID> expectedIds =
        List.of(firstId, secondId, thirdId).stream()
            .sorted(Comparator.comparing(UUID::toString))
            .toList();

    var response =
        applicationService.list(
            userId, ApplicationListQuery.fromRequestParams(0, 20, null, null, "createdAt", "asc"));

    assertThat(response.items().stream().map(item -> item.id()).toList())
        .containsExactlyElementsOf(expectedIds);
  }

  @Test
  void listIsIsolatedByUser() {
    UUID firstUserId = createUser("list-isolation-1@example.com");
    UUID secondUserId = createUser("list-isolation-2@example.com");
    createApplication(firstUserId, "Acme", "Backend Engineer", ApplicationStage.APPLIED);
    createApplication(secondUserId, "Globex", "Frontend Engineer", ApplicationStage.OFFER);

    var response = applicationService.list(firstUserId);

    assertThat(response.items()).extracting("companyName").containsExactly("Acme");
    assertThat(response.totalItems()).isEqualTo(1);
  }

  @Test
  void boardReturnsEveryStageWithCountsAndFixedPageSize() {
    UUID userId = createUser("board-page-size@example.com");
    for (int index = 0; index < 21; index++) {
      createApplication(userId, "Applied " + index, "Engineer", ApplicationStage.APPLIED);
    }
    createApplication(userId, "Offer Co", "Engineer", ApplicationStage.OFFER);

    var response = applicationService.board(userId, ApplicationBoardQuery.initial(null));

    assertThat(response.columns())
        .extracting("stage")
        .containsExactly(
            ApplicationStage.INITIAL,
            ApplicationStage.APPLIED,
            ApplicationStage.INTERVIEWING,
            ApplicationStage.OFFER,
            ApplicationStage.REJECTED);
    var applied =
        response.columns().stream()
            .filter(column -> column.stage() == ApplicationStage.APPLIED)
            .findFirst()
            .orElseThrow();
    assertThat(applied.items()).hasSize(ApplicationService.BOARD_COLUMN_PAGE_SIZE);
    assertThat(applied.totalCount()).isEqualTo(21);
    assertThat(applied.nextOffset()).isEqualTo(20);
    assertThat(applied.hasMore()).isTrue();
    assertThat(response.columns().getFirst().items()).isEmpty();
  }

  @Test
  void boardSearchAndColumnPaginationAreUserIsolated() {
    UUID firstUserId = createUser("board-isolation-1@example.com");
    UUID secondUserId = createUser("board-isolation-2@example.com");
    for (int index = 0; index < 21; index++) {
      createApplication(firstUserId, "Acme " + index, "Backend Engineer", ApplicationStage.APPLIED);
    }
    createApplication(firstUserId, "Globex", "Frontend Engineer", ApplicationStage.INTERVIEWING);
    createApplication(secondUserId, "Acme Foreign", "Backend Engineer", ApplicationStage.APPLIED);

    var board = applicationService.board(firstUserId, ApplicationBoardQuery.initial("  ACME  "));
    var applied =
        board.columns().stream()
            .filter(column -> column.stage() == ApplicationStage.APPLIED)
            .findFirst()
            .orElseThrow();
    var nextPage =
        applicationService.boardColumn(
            firstUserId, ApplicationStage.APPLIED, ApplicationBoardQuery.column("acme", 20));

    assertThat(applied.totalCount()).isEqualTo(21);
    assertThat(applied.items()).hasSize(20);
    assertThat(applied.items()).extracting("companyName").doesNotContain("Acme Foreign");
    assertThat(nextPage.stage()).isEqualTo(ApplicationStage.APPLIED);
    assertThat(nextPage.items()).hasSize(1);
    assertThat(nextPage.totalCount()).isEqualTo(21);
    assertThat(nextPage.nextOffset()).isEqualTo(21);
    assertThat(nextPage.hasMore()).isFalse();
    assertThat(
            board.columns().stream()
                .filter(column -> column.stage() == ApplicationStage.INTERVIEWING)
                .findFirst()
                .orElseThrow()
                .items())
        .isEmpty();
  }

  @Test
  void boardAndColumnApplySortAndDirection() {
    UUID userId = createUser("board-sort@example.com");
    UUID firstId = createApplication(userId, "First", "Engineer", ApplicationStage.APPLIED);
    UUID secondId = createApplication(userId, "Second", "Engineer", ApplicationStage.APPLIED);
    UUID thirdId = createApplication(userId, "Third", "Engineer", ApplicationStage.APPLIED);
    OffsetDateTime firstCreatedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    OffsetDateTime secondCreatedAt = OffsetDateTime.parse("2026-02-01T00:00:00Z");
    OffsetDateTime thirdCreatedAt = OffsetDateTime.parse("2026-03-01T00:00:00Z");
    dsl.update(JOB_APPLICATIONS)
        .set(JOB_APPLICATIONS.CREATED_AT, firstCreatedAt)
        .where(JOB_APPLICATIONS.ID.eq(firstId))
        .execute();
    dsl.update(JOB_APPLICATIONS)
        .set(JOB_APPLICATIONS.CREATED_AT, secondCreatedAt)
        .where(JOB_APPLICATIONS.ID.eq(secondId))
        .execute();
    dsl.update(JOB_APPLICATIONS)
        .set(JOB_APPLICATIONS.CREATED_AT, thirdCreatedAt)
        .where(JOB_APPLICATIONS.ID.eq(thirdId))
        .execute();

    var board =
        applicationService.board(userId, ApplicationBoardQuery.initial(null, "createdAt", "asc"));
    var applied =
        board.columns().stream()
            .filter(column -> column.stage() == ApplicationStage.APPLIED)
            .findFirst()
            .orElseThrow();
    var column =
        applicationService.boardColumn(
            userId,
            ApplicationStage.APPLIED,
            ApplicationBoardQuery.column(null, 1, "createdAt", "desc"));

    assertThat(applied.items()).extracting("id").containsExactly(firstId, secondId, thirdId);
    assertThat(column.items()).extracting("id").containsExactly(secondId, firstId);
  }

  @Test
  void getReturnsOwnApplicationById() {
    UUID userId = createUser("get-own@example.com");
    UUID applicationId =
        createApplication(userId, "Acme", "Backend Engineer", ApplicationStage.APPLIED);

    var response = applicationService.get(userId, applicationId);

    assertThat(response.id()).isEqualTo(applicationId);
    assertThat(response.companyName()).isEqualTo("Acme");
  }

  @Test
  void getReturnsNotFoundForMissingOrForeignApplication() {
    UUID firstUserId = createUser("get-foreign-1@example.com");
    UUID secondUserId = createUser("get-foreign-2@example.com");
    UUID foreignApplicationId =
        createApplication(secondUserId, "Globex", "Frontend Engineer", ApplicationStage.OFFER);

    assertThatThrownBy(() -> applicationService.get(firstUserId, UUID.randomUUID()))
        .isInstanceOf(ApplicationNotFoundException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("APPLICATION_NOT_FOUND");
    assertThatThrownBy(() -> applicationService.get(firstUserId, foreignApplicationId))
        .isInstanceOf(ApplicationNotFoundException.class)
        .extracting(error -> ((DomainException) error).code())
        .isEqualTo("APPLICATION_NOT_FOUND");
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
                null,
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

  private UUID createApplication(
      UUID userId, String companyName, String positionTitle, ApplicationStage stage) {
    return applicationService
        .create(
            userId,
            new CreateApplicationRequest(
                companyName, positionTitle, null, null, null, stage, null, null, null))
        .application()
        .id();
  }
}
