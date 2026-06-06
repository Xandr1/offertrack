package com.offertrack;

import static com.offertrack.jooq.generated.tables.ApplicationInterviews.APPLICATION_INTERVIEWS;
import static com.offertrack.jooq.generated.tables.JobApplications.JOB_APPLICATIONS;
import static org.assertj.core.api.Assertions.assertThat;

import com.offertrack.applications.ApplicationStage;
import com.offertrack.dashboard.DashboardApplicationItem;
import com.offertrack.dashboard.DashboardInterviewItem;
import com.offertrack.dashboard.DashboardRepository;
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
  @Autowired private DSLContext dsl;
  @Autowired private UserRepository userRepository;

  @BeforeEach
  void cleanDatabase() {
    dsl.execute("delete from application_interviews");
    dsl.execute("delete from job_applications");
    dsl.execute("delete from users");
  }

  @Test
  void draftsModuleReturnsInitialApplicationsOrderedByOldestUpdateAndLimited() {
    UUID userId = createUser("drafts@example.com");
    UUID oldestDraft =
        createApplication(
            userId, "Draft 0", ApplicationStage.INITIAL, null, NOW.minusDays(20), NOW.minusDays(6));

    for (int index = 1; index < 6; index++) {
      createApplication(
          userId,
          "Draft " + index,
          ApplicationStage.INITIAL,
          null,
          NOW.minusDays(20 - index),
          NOW.minusDays(6 - index));
    }

    createApplication(
        userId, "Applied", ApplicationStage.APPLIED, NOW.minusDays(10), NOW.minusDays(10), NOW);

    assertThat(dashboardRepository.countDraftsToApply(userId)).isEqualTo(6);
    assertThat(dashboardRepository.listDraftsToApply(userId, 5))
        .hasSize(5)
        .extracting(DashboardApplicationItem::applicationId)
        .first()
        .isEqualTo(oldestDraft);
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

    assertThat(dashboardRepository.countApplicationsToFollowUp(userId, NOW.minusDays(7), NOW))
        .isEqualTo(1);
    assertThat(dashboardRepository.listApplicationsToFollowUp(userId, NOW.minusDays(7), NOW, 5))
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

    assertThat(dashboardRepository.listApplicationsToFollowUp(userId, NOW.minusDays(7), NOW, 5))
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

    assertThat(dashboardRepository.countApplicationsToFollowUp(userId, NOW.minusDays(7), NOW))
        .isZero();
    assertThat(dashboardRepository.listApplicationsToFollowUp(userId, NOW.minusDays(7), NOW, 5))
        .isEmpty();
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
    createInterview(userId, firstApplication, InterviewStatus.COMPLETED, NOW.plusDays(1));

    assertThat(dashboardRepository.countUpcomingInterviews(userId, NOW, NOW.plusDays(7)))
        .isEqualTo(2);
    assertThat(dashboardRepository.listUpcomingInterviews(userId, NOW, NOW.plusDays(7), 5))
        .extracting(DashboardInterviewItem::interviewId)
        .containsExactly(firstInterview, secondInterview);
  }

  @Test
  void interviewsToFollowUpReturnsCompletedInterviewsOlderThanTwoDays() {
    UUID userId = createUser("interview-follow-up@example.com");
    UUID applicationId =
        createApplication(userId, "Completed", ApplicationStage.INTERVIEWING, null, NOW, NOW);
    UUID staleCompleted =
        createInterview(userId, applicationId, InterviewStatus.COMPLETED, NOW.minusDays(3));
    createInterview(userId, applicationId, InterviewStatus.COMPLETED, NOW.minusDays(1));
    createInterview(userId, applicationId, InterviewStatus.SCHEDULED, NOW.minusDays(5));

    assertThat(dashboardRepository.countInterviewsToFollowUp(userId, NOW.minusDays(2)))
        .isEqualTo(1);
    assertThat(dashboardRepository.listInterviewsToFollowUp(userId, NOW.minusDays(2), 5))
        .extracting(DashboardInterviewItem::interviewId)
        .containsExactly(staleCompleted);
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

    assertThat(dashboardRepository.listUpcomingInterviews(userId, NOW, NOW.plusDays(7), 5))
        .extracting(DashboardInterviewItem::interviewId)
        .containsExactly(ownedInterviewWithForeignInterviewUser);
  }

  @Test
  void moduleLimitsApplyWhileCountsStayUncapped() {
    UUID userId = createUser("limits@example.com");

    for (int index = 0; index < 6; index++) {
      createApplication(
          userId,
          "Limited " + index,
          ApplicationStage.INITIAL,
          null,
          NOW.minusDays(20 - index),
          NOW.minusDays(6 - index));
    }

    assertThat(dashboardRepository.countDraftsToApply(userId)).isEqualTo(6);
    assertThat(dashboardRepository.listDraftsToApply(userId, 3)).hasSize(3);
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
    UUID interviewId = UUID.randomUUID();

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
