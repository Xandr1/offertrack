package com.offertrack;

import static com.offertrack.jooq.generated.tables.ApplicationInterviews.APPLICATION_INTERVIEWS;
import static com.offertrack.jooq.generated.tables.JobApplications.JOB_APPLICATIONS;
import static org.assertj.core.api.Assertions.assertThat;

import com.offertrack.applications.ApplicationStage;
import com.offertrack.dashboard.DashboardApplicationItem;
import com.offertrack.dashboard.DashboardInterviewItem;
import com.offertrack.dashboard.DashboardRepository;
import com.offertrack.interviews.ApplicationInterviewRepository;
import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import com.offertrack.users.UserRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DashboardRepositoryIntegrationTest {
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-06T12:00:00Z");

  @Autowired private DashboardRepository dashboardRepository;
  @Autowired private ApplicationInterviewRepository applicationInterviewRepository;
  @Autowired private DSLContext dsl;
  @Autowired private UserRepository userRepository;

  @BeforeEach
  void cleanDatabase() {
    dsl.execute("delete from application_interviews");
    dsl.execute("delete from job_applications");
    dsl.execute("delete from users");
  }

  @Test
  void applicationsFollowUpUsesAppliedAtThreshold() {
    UUID userId = createUser("applied-threshold@example.com");
    UUID staleApplied =
        createApplication(
            userId,
            "Stale Applied",
            ApplicationStage.APPLIED,
            NOW.minusDays(8),
            NOW.minusDays(9),
            NOW.minusDays(1));
    createApplication(
        userId,
        "Recent Applied",
        ApplicationStage.APPLIED,
        NOW.minusDays(6),
        NOW.minusDays(20),
        NOW.minusDays(1));

    assertThat(dashboardRepository.countApplicationsToFollowUp(userId, NOW.minusDays(7)))
        .isEqualTo(1);
    assertThat(dashboardRepository.listApplicationsToFollowUp(userId, NOW.minusDays(7), 0, 5))
        .extracting(DashboardApplicationItem::applicationId)
        .containsExactly(staleApplied);
  }

  @Test
  void applicationsFollowUpFallsBackToCreatedAtWhenAppliedAtIsNull() {
    UUID userId = createUser("created-fallback@example.com");
    UUID staleCreated =
        createApplication(
            userId,
            "Stale Created",
            ApplicationStage.APPLIED,
            null,
            NOW.minusDays(8),
            NOW.minusDays(1));
    createApplication(
        userId, "Recent Created", ApplicationStage.APPLIED, null, NOW.minusDays(6), NOW);

    assertThat(dashboardRepository.listApplicationsToFollowUp(userId, NOW.minusDays(7), 0, 5))
        .extracting(DashboardApplicationItem::applicationId)
        .containsExactly(staleCreated);
  }

  @Test
  void applicationsFollowUpExcludesApplicationsWithFutureScheduledInterviews() {
    UUID userId = createUser("future-interview@example.com");
    UUID applicationId =
        createApplication(
            userId,
            "Has Interview",
            ApplicationStage.APPLIED,
            NOW.minusDays(8),
            NOW.minusDays(9),
            NOW.minusDays(1));
    createInterview(userId, applicationId, InterviewStatus.SCHEDULED, NOW.plusDays(1));

    assertThat(dashboardRepository.countApplicationsToFollowUp(userId, NOW.minusDays(7))).isZero();
    assertThat(dashboardRepository.listApplicationsToFollowUp(userId, NOW.minusDays(7), 0, 5))
        .isEmpty();
  }

  @Test
  void applicationsFollowUpExcludesApplicationsWithUndatedScheduledInterviews() {
    UUID userId = createUser("undated-interview@example.com");
    UUID applicationId =
        createApplication(
            userId,
            "Has Planned Interview",
            ApplicationStage.APPLIED,
            NOW.minusDays(8),
            NOW.minusDays(9),
            NOW.minusDays(1));
    createInterview(userId, applicationId, InterviewStatus.SCHEDULED, null);

    assertThat(dashboardRepository.countApplicationsToFollowUp(userId, NOW.minusDays(7))).isZero();
    assertThat(dashboardRepository.listApplicationsToFollowUp(userId, NOW.minusDays(7), 0, 5))
        .isEmpty();
  }

  @Test
  void applicationsFollowUpAllowsInitialInterviewsAndExcludesFollowedUpApplications() {
    UUID userId = createUser("initial-interview@example.com");
    UUID applicationId =
        createApplication(
            userId,
            "Initial Interview",
            ApplicationStage.APPLIED,
            NOW.minusDays(8),
            NOW.minusDays(9),
            NOW.minusDays(1));
    createInterview(userId, applicationId, InterviewStatus.INITIAL, null);

    assertThat(dashboardRepository.listApplicationsToFollowUp(userId, NOW.minusDays(7), 0, 5))
        .extracting(DashboardApplicationItem::applicationId)
        .containsExactly(applicationId);

    dsl.update(JOB_APPLICATIONS)
        .set(JOB_APPLICATIONS.FOLLOWED_UP_AT, NOW)
        .where(JOB_APPLICATIONS.ID.eq(applicationId))
        .execute();

    assertThat(dashboardRepository.countApplicationsToFollowUp(userId, NOW.minusDays(7))).isZero();
  }

  @Test
  void upcomingInterviewsReturnsScheduledInterviewsWithinSevenDays() {
    UUID userId = createUser("upcoming@example.com");
    UUID firstApplication =
        createApplication(userId, "First", ApplicationStage.INTERVIEWING, null, NOW, NOW);
    UUID secondApplication =
        createApplication(userId, "Second", ApplicationStage.INTERVIEWING, null, NOW, NOW);
    UUID firstInterview =
        createInterview(userId, secondApplication, InterviewStatus.SCHEDULED, NOW.plusDays(2));
    UUID secondInterview =
        createInterview(userId, firstApplication, InterviewStatus.SCHEDULED, NOW.plusDays(7));
    createInterview(userId, firstApplication, InterviewStatus.SCHEDULED, NOW.plusDays(8));
    createInterview(userId, firstApplication, InterviewStatus.INITIAL, NOW.plusDays(1));

    assertThat(dashboardRepository.countUpcomingInterviews(userId, NOW, NOW.plusDays(7)))
        .isEqualTo(2);
    assertThat(dashboardRepository.listUpcomingInterviews(userId, NOW, NOW.plusDays(7), 0, 5))
        .extracting(DashboardInterviewItem::interviewId)
        .containsExactly(firstInterview, secondInterview);
  }

  @Test
  void upcomingInterviewsExcludesUndatedScheduledInterviews() {
    UUID userId = createUser("undated-upcoming@example.com");
    UUID applicationId =
        createApplication(userId, "Undated", ApplicationStage.INTERVIEWING, null, NOW, NOW);
    createInterview(userId, applicationId, InterviewStatus.SCHEDULED, null);

    assertThat(dashboardRepository.countUpcomingInterviews(userId, NOW, NOW.plusDays(7))).isZero();
    assertThat(dashboardRepository.listUpcomingInterviews(userId, NOW, NOW.plusDays(7), 0, 5))
        .isEmpty();
  }

  @Test
  void interviewsToFollowUpReturnsScheduledInterviewsOlderThanTwoDays() {
    UUID userId = createUser("interview-follow-up@example.com");
    UUID applicationId =
        createApplication(userId, "Scheduled", ApplicationStage.INTERVIEWING, null, NOW, NOW);
    UUID staleScheduled =
        createInterview(userId, applicationId, InterviewStatus.SCHEDULED, NOW.minusDays(3));
    createInterview(userId, applicationId, InterviewStatus.SCHEDULED, NOW.minusDays(5));

    assertThat(dashboardRepository.countInterviewsToFollowUp(userId, NOW, NOW.minusDays(2)))
        .isEqualTo(1);
    assertThat(dashboardRepository.listInterviewsToFollowUp(userId, NOW, NOW.minusDays(2), 0, 5))
        .extracting(DashboardInterviewItem::interviewId)
        .containsExactly(staleScheduled);
  }

  @Test
  void interviewsToFollowUpExcludesFollowedUpAndFutureScheduledInterviews() {
    UUID userId = createUser("interview-follow-up-exclusions@example.com");
    UUID followedUpApplication =
        createApplication(userId, "Followed Up", ApplicationStage.INTERVIEWING, null, NOW, NOW);
    UUID followedUpInterview =
        createInterview(userId, followedUpApplication, InterviewStatus.SCHEDULED, NOW.minusDays(3));
    dsl.update(APPLICATION_INTERVIEWS)
        .set(APPLICATION_INTERVIEWS.FOLLOWED_UP_AT, NOW)
        .where(APPLICATION_INTERVIEWS.ID.eq(followedUpInterview))
        .execute();

    UUID futureApplication =
        createApplication(userId, "Future", ApplicationStage.INTERVIEWING, null, NOW, NOW);
    createInterview(userId, futureApplication, InterviewStatus.SCHEDULED, NOW.minusDays(3));
    createInterview(userId, futureApplication, InterviewStatus.SCHEDULED, NOW.plusDays(1));

    assertThat(dashboardRepository.countInterviewsToFollowUp(userId, NOW, NOW.minusDays(2)))
        .isZero();
    assertThat(dashboardRepository.listInterviewsToFollowUp(userId, NOW, NOW.minusDays(2), 0, 5))
        .isEmpty();
  }

  @Test
  void interviewsToFollowUpExcludesUndatedScheduledInterviews() {
    UUID userId = createUser("undated-follow-up@example.com");
    UUID applicationId =
        createApplication(userId, "Undated", ApplicationStage.INTERVIEWING, null, NOW, NOW);
    createInterview(userId, applicationId, InterviewStatus.SCHEDULED, null);

    assertThat(dashboardRepository.countInterviewsToFollowUp(userId, NOW, NOW.minusDays(2)))
        .isZero();
    assertThat(dashboardRepository.listInterviewsToFollowUp(userId, NOW, NOW.minusDays(2), 0, 5))
        .isEmpty();
  }

  @Test
  void applicationSummaryAndDashboardUseSmallerIdForTiedPastInterviews() {
    UUID userId = createUser("past-interview-tie@example.com");
    UUID applicationId =
        createApplication(userId, "Tied", ApplicationStage.INTERVIEWING, null, NOW, NOW);
    UUID smallerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID largerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    OffsetDateTime scheduledAt = NOW.minusDays(3);
    createInterview(smallerId, userId, applicationId, InterviewStatus.SCHEDULED, scheduledAt);
    createInterview(largerId, userId, applicationId, InterviewStatus.SCHEDULED, scheduledAt);

    assertThat(
            applicationInterviewRepository
                .findLastByApplicationIdsForUser(userId, java.util.List.of(applicationId), NOW)
                .get(applicationId)
                .id())
        .isEqualTo(smallerId);
    assertThat(dashboardRepository.listInterviewsToFollowUp(userId, NOW, NOW.minusDays(2), 0, 5))
        .extracting(DashboardInterviewItem::interviewId)
        .containsExactly(smallerId);
  }

  @Test
  void interviewModulesEnforceOwnershipThroughJoinedApplicationUser() {
    UUID userId = createUser("owner@example.com");
    UUID foreignUserId = createUser("foreign@example.com");
    UUID ownedApplication =
        createApplication(userId, "Owned", ApplicationStage.INTERVIEWING, null, NOW, NOW);
    UUID foreignApplication =
        createApplication(foreignUserId, "Foreign", ApplicationStage.INTERVIEWING, null, NOW, NOW);
    UUID ownedInterviewWithForeignInterviewUser =
        createInterview(
            foreignUserId, ownedApplication, InterviewStatus.SCHEDULED, NOW.plusDays(1));
    createInterview(userId, foreignApplication, InterviewStatus.SCHEDULED, NOW.plusDays(1));

    assertThat(dashboardRepository.listUpcomingInterviews(userId, NOW, NOW.plusDays(7), 0, 5))
        .extracting(DashboardInterviewItem::interviewId)
        .containsExactly(ownedInterviewWithForeignInterviewUser);
  }

  private UUID createUser(String email) {
    return userRepository.createUser(email, "hash", "Test User").id();
  }

  private UUID createApplication(
      UUID userId,
      String companyName,
      ApplicationStage stage,
      OffsetDateTime appliedAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    UUID applicationId = UUID.randomUUID();

    dsl.insertInto(JOB_APPLICATIONS)
        .set(JOB_APPLICATIONS.ID, applicationId)
        .set(JOB_APPLICATIONS.USER_ID, userId)
        .set(JOB_APPLICATIONS.COMPANY_NAME, companyName)
        .set(JOB_APPLICATIONS.POSITION_TITLE, "Backend Engineer")
        .set(JOB_APPLICATIONS.JOB_URL, "https://example.com/" + applicationId)
        .set(JOB_APPLICATIONS.LOCATION, "Remote")
        .set(JOB_APPLICATIONS.WORK_MODE, "remote")
        .set(JOB_APPLICATIONS.STAGE, stage.value())
        .set(JOB_APPLICATIONS.NOTES, (String) null)
        .set(JOB_APPLICATIONS.APPLIED_AT, appliedAt)
        .set(JOB_APPLICATIONS.CREATED_AT, createdAt)
        .set(JOB_APPLICATIONS.UPDATED_AT, updatedAt)
        .execute();

    return applicationId;
  }

  private UUID createInterview(
      UUID interviewUserId,
      UUID applicationId,
      InterviewStatus status,
      OffsetDateTime scheduledAt) {
    return createInterview(UUID.randomUUID(), interviewUserId, applicationId, status, scheduledAt);
  }

  private UUID createInterview(
      UUID interviewId,
      UUID interviewUserId,
      UUID applicationId,
      InterviewStatus status,
      OffsetDateTime scheduledAt) {

    dsl.insertInto(APPLICATION_INTERVIEWS)
        .set(APPLICATION_INTERVIEWS.ID, interviewId)
        .set(APPLICATION_INTERVIEWS.USER_ID, interviewUserId)
        .set(APPLICATION_INTERVIEWS.APPLICATION_ID, applicationId)
        .set(APPLICATION_INTERVIEWS.TYPE, InterviewType.TECHNICAL.value())
        .set(APPLICATION_INTERVIEWS.STATUS, status.value())
        .set(APPLICATION_INTERVIEWS.SCHEDULED_AT, scheduledAt)
        .set(APPLICATION_INTERVIEWS.CREATED_AT, NOW.minusDays(10))
        .set(APPLICATION_INTERVIEWS.UPDATED_AT, NOW.minusDays(1))
        .execute();

    return interviewId;
  }
}
