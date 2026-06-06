package com.offertrack.dashboard;

import static com.offertrack.jooq.generated.tables.ApplicationInterviews.APPLICATION_INTERVIEWS;
import static com.offertrack.jooq.generated.tables.JobApplications.JOB_APPLICATIONS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.selectOne;

import com.offertrack.applications.ApplicationStage;
import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {
  private static final List<String> ACTIVE_STAGE_VALUES =
      List.of(
          ApplicationStage.INITIAL.value(),
          ApplicationStage.APPLIED.value(),
          ApplicationStage.INTERVIEWING.value());

  private final DSLContext dsl;

  public DashboardRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public DashboardSummaryCounts getSummaryCounts(UUID userId) {
    return dsl.select(
            count()
                .filterWhere(JOB_APPLICATIONS.STAGE.in(ACTIVE_STAGE_VALUES))
                .as("active_processes"),
            count()
                .filterWhere(JOB_APPLICATIONS.STAGE.eq(ApplicationStage.INTERVIEWING.value()))
                .as("interviewing"),
            count()
                .filterWhere(JOB_APPLICATIONS.STAGE.eq(ApplicationStage.OFFER.value()))
                .as("offers"),
            count()
                .filterWhere(JOB_APPLICATIONS.STAGE.eq(ApplicationStage.REJECTED.value()))
                .as("rejected"))
        .from(JOB_APPLICATIONS)
        .where(JOB_APPLICATIONS.USER_ID.eq(userId))
        .fetchSingle(
            record ->
                new DashboardSummaryCounts(
                    record.value1(), record.value2(), record.value3(), record.value4()));
  }

  public long countDraftsToApply(UUID userId) {
    return dsl.selectCount()
        .from(JOB_APPLICATIONS)
        .where(draftsToApplyCondition(userId))
        .fetchSingle(0, long.class);
  }

  public List<DashboardApplicationItem> listDraftsToApply(UUID userId, int limit) {
    return dsl.select(
            JOB_APPLICATIONS.ID,
            JOB_APPLICATIONS.COMPANY_NAME,
            JOB_APPLICATIONS.POSITION_TITLE,
            JOB_APPLICATIONS.STAGE,
            JOB_APPLICATIONS.JOB_URL,
            JOB_APPLICATIONS.LOCATION,
            JOB_APPLICATIONS.WORK_MODE,
            JOB_APPLICATIONS.APPLIED_AT,
            JOB_APPLICATIONS.UPDATED_AT)
        .from(JOB_APPLICATIONS)
        .where(draftsToApplyCondition(userId))
        .orderBy(JOB_APPLICATIONS.UPDATED_AT.asc())
        .limit(limit)
        .fetch(this::toApplicationItem);
  }

  public long countApplicationsToFollowUp(
      UUID userId, OffsetDateTime followUpBefore, OffsetDateTime now) {
    return dsl.selectCount()
        .from(JOB_APPLICATIONS)
        .where(applicationsToFollowUpCondition(userId, followUpBefore, now))
        .fetchSingle(0, long.class);
  }

  public List<DashboardApplicationItem> listApplicationsToFollowUp(
      UUID userId, OffsetDateTime followUpBefore, OffsetDateTime now, int limit) {
    Field<OffsetDateTime> appliedOrCreatedAt = appliedOrCreatedAt();

    return dsl.select(
            JOB_APPLICATIONS.ID,
            JOB_APPLICATIONS.COMPANY_NAME,
            JOB_APPLICATIONS.POSITION_TITLE,
            JOB_APPLICATIONS.STAGE,
            JOB_APPLICATIONS.JOB_URL,
            JOB_APPLICATIONS.LOCATION,
            JOB_APPLICATIONS.WORK_MODE,
            JOB_APPLICATIONS.APPLIED_AT,
            JOB_APPLICATIONS.UPDATED_AT)
        .from(JOB_APPLICATIONS)
        .where(applicationsToFollowUpCondition(userId, followUpBefore, now))
        .orderBy(appliedOrCreatedAt.asc())
        .limit(limit)
        .fetch(this::toApplicationItem);
  }

  public long countUpcomingInterviews(
      UUID userId, OffsetDateTime now, OffsetDateTime upcomingBefore) {
    return countInterviews(upcomingInterviewsCondition(userId, now, upcomingBefore));
  }

  public List<DashboardInterviewItem> listUpcomingInterviews(
      UUID userId, OffsetDateTime now, OffsetDateTime upcomingBefore, int limit) {
    return listInterviews(upcomingInterviewsCondition(userId, now, upcomingBefore), limit);
  }

  public long countInterviewsToFollowUp(UUID userId, OffsetDateTime followUpBefore) {
    return countInterviews(interviewsToFollowUpCondition(userId, followUpBefore));
  }

  public List<DashboardInterviewItem> listInterviewsToFollowUp(
      UUID userId, OffsetDateTime followUpBefore, int limit) {
    return listInterviews(interviewsToFollowUpCondition(userId, followUpBefore), limit);
  }

  private long countInterviews(Condition condition) {
    return dsl.selectCount()
        .from(APPLICATION_INTERVIEWS)
        .join(JOB_APPLICATIONS)
        .on(APPLICATION_INTERVIEWS.APPLICATION_ID.eq(JOB_APPLICATIONS.ID))
        .where(condition)
        .fetchSingle(0, long.class);
  }

  private List<DashboardInterviewItem> listInterviews(Condition condition, int limit) {
    return dsl.select(
            APPLICATION_INTERVIEWS.APPLICATION_ID,
            APPLICATION_INTERVIEWS.ID,
            JOB_APPLICATIONS.COMPANY_NAME,
            JOB_APPLICATIONS.POSITION_TITLE,
            JOB_APPLICATIONS.JOB_URL,
            JOB_APPLICATIONS.LOCATION,
            JOB_APPLICATIONS.WORK_MODE,
            APPLICATION_INTERVIEWS.SCHEDULED_AT,
            APPLICATION_INTERVIEWS.TYPE,
            APPLICATION_INTERVIEWS.STATUS)
        .from(APPLICATION_INTERVIEWS)
        .join(JOB_APPLICATIONS)
        .on(APPLICATION_INTERVIEWS.APPLICATION_ID.eq(JOB_APPLICATIONS.ID))
        .where(condition)
        .orderBy(APPLICATION_INTERVIEWS.SCHEDULED_AT.asc())
        .limit(limit)
        .fetch(
            record ->
                new DashboardInterviewItem(
                    record.get(APPLICATION_INTERVIEWS.APPLICATION_ID),
                    record.get(APPLICATION_INTERVIEWS.ID),
                    record.get(JOB_APPLICATIONS.COMPANY_NAME),
                    record.get(JOB_APPLICATIONS.POSITION_TITLE),
                    record.get(JOB_APPLICATIONS.JOB_URL),
                    record.get(JOB_APPLICATIONS.LOCATION),
                    record.get(JOB_APPLICATIONS.WORK_MODE),
                    record.get(APPLICATION_INTERVIEWS.SCHEDULED_AT),
                    InterviewType.fromValue(record.get(APPLICATION_INTERVIEWS.TYPE)),
                    InterviewStatus.fromValue(record.get(APPLICATION_INTERVIEWS.STATUS))));
  }

  private DashboardApplicationItem toApplicationItem(
      org.jooq.Record9<
              UUID, String, String, String, String, String, String, OffsetDateTime, OffsetDateTime>
          record) {
    return new DashboardApplicationItem(
        record.get(JOB_APPLICATIONS.ID),
        record.get(JOB_APPLICATIONS.COMPANY_NAME),
        record.get(JOB_APPLICATIONS.POSITION_TITLE),
        ApplicationStage.fromValue(record.get(JOB_APPLICATIONS.STAGE)),
        record.get(JOB_APPLICATIONS.JOB_URL),
        record.get(JOB_APPLICATIONS.LOCATION),
        record.get(JOB_APPLICATIONS.WORK_MODE),
        record.get(JOB_APPLICATIONS.APPLIED_AT),
        record.get(JOB_APPLICATIONS.UPDATED_AT));
  }

  private static Condition draftsToApplyCondition(UUID userId) {
    return JOB_APPLICATIONS
        .USER_ID
        .eq(userId)
        .and(JOB_APPLICATIONS.STAGE.eq(ApplicationStage.INITIAL.value()));
  }

  private static Condition applicationsToFollowUpCondition(
      UUID userId, OffsetDateTime followUpBefore, OffsetDateTime now) {
    return JOB_APPLICATIONS
        .USER_ID
        .eq(userId)
        .and(JOB_APPLICATIONS.STAGE.eq(ApplicationStage.APPLIED.value()))
        .and(appliedOrCreatedAt().le(followUpBefore))
        .andNot(hasFutureScheduledInterview(now));
  }

  private static Condition hasFutureScheduledInterview(OffsetDateTime now) {
    return exists(
        selectOne()
            .from(APPLICATION_INTERVIEWS)
            .where(APPLICATION_INTERVIEWS.APPLICATION_ID.eq(JOB_APPLICATIONS.ID))
            .and(APPLICATION_INTERVIEWS.STATUS.eq(InterviewStatus.SCHEDULED.value()))
            .and(APPLICATION_INTERVIEWS.SCHEDULED_AT.ge(now)));
  }

  private static Condition upcomingInterviewsCondition(
      UUID userId, OffsetDateTime now, OffsetDateTime upcomingBefore) {
    return JOB_APPLICATIONS
        .USER_ID
        .eq(userId)
        .and(APPLICATION_INTERVIEWS.STATUS.eq(InterviewStatus.SCHEDULED.value()))
        .and(APPLICATION_INTERVIEWS.SCHEDULED_AT.ge(now))
        .and(APPLICATION_INTERVIEWS.SCHEDULED_AT.le(upcomingBefore));
  }

  private static Condition interviewsToFollowUpCondition(
      UUID userId, OffsetDateTime followUpBefore) {
    return JOB_APPLICATIONS
        .USER_ID
        .eq(userId)
        .and(APPLICATION_INTERVIEWS.STATUS.eq(InterviewStatus.COMPLETED.value()))
        .and(APPLICATION_INTERVIEWS.SCHEDULED_AT.le(followUpBefore));
  }

  private static Field<OffsetDateTime> appliedOrCreatedAt() {
    return coalesce(JOB_APPLICATIONS.APPLIED_AT, JOB_APPLICATIONS.CREATED_AT);
  }
}
