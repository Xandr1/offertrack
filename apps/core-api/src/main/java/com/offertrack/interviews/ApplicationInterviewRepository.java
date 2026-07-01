package com.offertrack.interviews;

import static com.offertrack.jooq.generated.tables.ApplicationInterviews.APPLICATION_INTERVIEWS;
import static com.offertrack.jooq.generated.tables.JobApplications.JOB_APPLICATIONS;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.when;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicationInterviewRepository {
  private static final List<String> LAST_INTERVIEW_STATUSES =
      List.of(
          InterviewStatus.SCHEDULED.value(),
          InterviewStatus.PASSED.value(),
          InterviewStatus.REJECTED.value());

  private final DSLContext dsl;

  public ApplicationInterviewRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public List<ApplicationInterview> listByApplicationForUser(UUID applicationId, UUID userId) {
    return dsl.selectFrom(APPLICATION_INTERVIEWS)
        .where(APPLICATION_INTERVIEWS.APPLICATION_ID.eq(applicationId))
        .and(APPLICATION_INTERVIEWS.USER_ID.eq(userId))
        .orderBy(
            APPLICATION_INTERVIEWS.SCHEDULED_AT.asc().nullsLast(),
            APPLICATION_INTERVIEWS.CREATED_AT.asc())
        .fetch(ApplicationInterviewMapper::fromRecord);
  }

  public ApplicationInterview create(
      UUID applicationId,
      UUID userId,
      InterviewType type,
      InterviewStatus status,
      OffsetDateTime scheduledAt) {
    UUID id = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();

    return dsl.insertInto(APPLICATION_INTERVIEWS)
        .set(APPLICATION_INTERVIEWS.ID, id)
        .set(APPLICATION_INTERVIEWS.USER_ID, userId)
        .set(APPLICATION_INTERVIEWS.APPLICATION_ID, applicationId)
        .set(APPLICATION_INTERVIEWS.TYPE, type.value())
        .set(APPLICATION_INTERVIEWS.STATUS, status.value())
        .set(APPLICATION_INTERVIEWS.SCHEDULED_AT, scheduledAt)
        .set(APPLICATION_INTERVIEWS.CREATED_AT, now)
        .set(APPLICATION_INTERVIEWS.UPDATED_AT, now)
        .returning()
        .fetchOne(ApplicationInterviewMapper::fromRecord);
  }

  public Optional<ApplicationInterview> replace(
      UUID applicationId,
      UUID interviewId,
      UUID userId,
      InterviewType type,
      InterviewStatus status,
      OffsetDateTime scheduledAt) {
    OffsetDateTime now = OffsetDateTime.now();

    return dsl.update(APPLICATION_INTERVIEWS)
        .set(APPLICATION_INTERVIEWS.TYPE, type.value())
        .set(APPLICATION_INTERVIEWS.STATUS, status.value())
        .set(APPLICATION_INTERVIEWS.SCHEDULED_AT, scheduledAt)
        .set(APPLICATION_INTERVIEWS.UPDATED_AT, now)
        .where(APPLICATION_INTERVIEWS.ID.eq(interviewId))
        .and(APPLICATION_INTERVIEWS.APPLICATION_ID.eq(applicationId))
        .and(APPLICATION_INTERVIEWS.USER_ID.eq(userId))
        .returning()
        .fetchOptional(ApplicationInterviewMapper::fromRecord);
  }

  public boolean delete(UUID applicationId, UUID interviewId, UUID userId) {
    int deletedRows =
        dsl.deleteFrom(APPLICATION_INTERVIEWS)
            .where(APPLICATION_INTERVIEWS.ID.eq(interviewId))
            .and(APPLICATION_INTERVIEWS.APPLICATION_ID.eq(applicationId))
            .and(APPLICATION_INTERVIEWS.USER_ID.eq(userId))
            .execute();

    return deletedRows > 0;
  }

  public Optional<ApplicationInterview> updateStatus(
      UUID applicationId, UUID interviewId, UUID userId, InterviewStatus status) {
    OffsetDateTime now = OffsetDateTime.now();

    return dsl.update(APPLICATION_INTERVIEWS)
        .set(APPLICATION_INTERVIEWS.STATUS, status.value())
        .set(APPLICATION_INTERVIEWS.UPDATED_AT, now)
        .where(APPLICATION_INTERVIEWS.ID.eq(interviewId))
        .and(APPLICATION_INTERVIEWS.APPLICATION_ID.eq(applicationId))
        .and(APPLICATION_INTERVIEWS.USER_ID.eq(userId))
        .returning()
        .fetchOptional(ApplicationInterviewMapper::fromRecord);
  }

  public Optional<ApplicationInterview> markFollowedUp(
      UUID applicationId, UUID interviewId, UUID userId, OffsetDateTime followedUpAt) {
    return dsl.update(APPLICATION_INTERVIEWS)
        .set(APPLICATION_INTERVIEWS.FOLLOWED_UP_AT, followedUpAt)
        .set(APPLICATION_INTERVIEWS.UPDATED_AT, followedUpAt)
        .where(APPLICATION_INTERVIEWS.ID.eq(interviewId))
        .and(APPLICATION_INTERVIEWS.APPLICATION_ID.eq(applicationId))
        .and(
            exists(
                selectOne()
                    .from(JOB_APPLICATIONS)
                    .where(JOB_APPLICATIONS.ID.eq(APPLICATION_INTERVIEWS.APPLICATION_ID))
                    .and(JOB_APPLICATIONS.USER_ID.eq(userId))))
        .returning()
        .fetchOptional(ApplicationInterviewMapper::fromRecord);
  }

  public int countByApplicationForUser(UUID applicationId, UUID userId) {
    return dsl.fetchCount(
        APPLICATION_INTERVIEWS,
        APPLICATION_INTERVIEWS
            .APPLICATION_ID
            .eq(applicationId)
            .and(APPLICATION_INTERVIEWS.USER_ID.eq(userId)));
  }

  public Optional<ApplicationInterview> findNextByApplicationForUser(
      UUID applicationId, UUID userId, OffsetDateTime now) {
    return dsl.selectFrom(APPLICATION_INTERVIEWS)
        .where(APPLICATION_INTERVIEWS.APPLICATION_ID.eq(applicationId))
        .and(APPLICATION_INTERVIEWS.USER_ID.eq(userId))
        .and(APPLICATION_INTERVIEWS.STATUS.eq(InterviewStatus.SCHEDULED.value()))
        .and(
            APPLICATION_INTERVIEWS
                .SCHEDULED_AT
                .isNull()
                .or(APPLICATION_INTERVIEWS.SCHEDULED_AT.ge(now)))
        .orderBy(
            APPLICATION_INTERVIEWS.SCHEDULED_AT.asc().nullsLast(),
            when(APPLICATION_INTERVIEWS.SCHEDULED_AT.isNull(), APPLICATION_INTERVIEWS.CREATED_AT)
                .asc()
                .nullsLast(),
            APPLICATION_INTERVIEWS.ID.asc())
        .limit(1)
        .fetchOptional(ApplicationInterviewMapper::fromRecord);
  }

  public Map<UUID, ApplicationInterview> findNextByApplicationIdsForUser(
      UUID userId, List<UUID> applicationIds, OffsetDateTime now) {
    if (applicationIds.isEmpty()) {
      return Map.of();
    }

    List<ApplicationInterview> interviews =
        dsl.selectFrom(APPLICATION_INTERVIEWS)
            .where(APPLICATION_INTERVIEWS.USER_ID.eq(userId))
            .and(APPLICATION_INTERVIEWS.APPLICATION_ID.in(applicationIds))
            .and(APPLICATION_INTERVIEWS.STATUS.eq(InterviewStatus.SCHEDULED.value()))
            .and(
                APPLICATION_INTERVIEWS
                    .SCHEDULED_AT
                    .isNull()
                    .or(APPLICATION_INTERVIEWS.SCHEDULED_AT.ge(now)))
            .orderBy(
                APPLICATION_INTERVIEWS.APPLICATION_ID.asc(),
                APPLICATION_INTERVIEWS.SCHEDULED_AT.asc().nullsLast(),
                when(
                        APPLICATION_INTERVIEWS.SCHEDULED_AT.isNull(),
                        APPLICATION_INTERVIEWS.CREATED_AT)
                    .asc()
                    .nullsLast(),
                APPLICATION_INTERVIEWS.ID.asc())
            .fetch(ApplicationInterviewMapper::fromRecord);

    return selectNextInterviewsByApplicationId(interviews, now);
  }

  static Map<UUID, ApplicationInterview> selectNextInterviewsByApplicationId(
      List<ApplicationInterview> interviews, OffsetDateTime now) {
    List<ApplicationInterview> sortedInterviews =
        interviews.stream()
            .filter(
                interview ->
                    interview.status() == InterviewStatus.SCHEDULED
                        && (interview.scheduledAt() == null
                            || !interview.scheduledAt().isBefore(now)))
            .sorted(ApplicationInterviewRepository::compareNextInterviews)
            .toList();

    Map<UUID, ApplicationInterview> nextByApplicationId = new HashMap<>();

    for (ApplicationInterview interview : sortedInterviews) {
      nextByApplicationId.putIfAbsent(interview.applicationId(), interview);
    }

    return nextByApplicationId;
  }

  private static int compareNextInterviews(ApplicationInterview left, ApplicationInterview right) {
    int applicationComparison = left.applicationId().compareTo(right.applicationId());
    if (applicationComparison != 0) {
      return applicationComparison;
    }

    if (left.scheduledAt() != null && right.scheduledAt() != null) {
      int scheduledAtComparison = left.scheduledAt().compareTo(right.scheduledAt());
      return scheduledAtComparison != 0 ? scheduledAtComparison : left.id().compareTo(right.id());
    }
    if (left.scheduledAt() != null) {
      return -1;
    }
    if (right.scheduledAt() != null) {
      return 1;
    }

    int createdAtComparison = left.createdAt().compareTo(right.createdAt());
    return createdAtComparison != 0 ? createdAtComparison : left.id().compareTo(right.id());
  }

  public Map<UUID, ApplicationInterview> findLastByApplicationIdsForUser(
      UUID userId, List<UUID> applicationIds, OffsetDateTime now) {
    if (applicationIds.isEmpty()) {
      return Map.of();
    }

    List<ApplicationInterview> interviews =
        dsl.selectFrom(APPLICATION_INTERVIEWS)
            .where(APPLICATION_INTERVIEWS.USER_ID.eq(userId))
            .and(APPLICATION_INTERVIEWS.APPLICATION_ID.in(applicationIds))
            .and(APPLICATION_INTERVIEWS.STATUS.in(LAST_INTERVIEW_STATUSES))
            .and(APPLICATION_INTERVIEWS.SCHEDULED_AT.isNotNull())
            .and(APPLICATION_INTERVIEWS.SCHEDULED_AT.lt(now))
            .orderBy(
                APPLICATION_INTERVIEWS.APPLICATION_ID.asc(),
                APPLICATION_INTERVIEWS.SCHEDULED_AT.desc(),
                APPLICATION_INTERVIEWS.ID.asc())
            .fetch(ApplicationInterviewMapper::fromRecord);

    return selectLastInterviewsByApplicationId(interviews);
  }

  static Map<UUID, ApplicationInterview> selectLastInterviewsByApplicationId(
      List<ApplicationInterview> interviews) {
    List<ApplicationInterview> sortedInterviews =
        interviews.stream()
            .filter(
                interview ->
                    interview.scheduledAt() != null
                        && LAST_INTERVIEW_STATUSES.contains(interview.status().value()))
            .sorted(
                Comparator.comparing(ApplicationInterview::applicationId)
                    .thenComparing(ApplicationInterview::scheduledAt, Comparator.reverseOrder())
                    .thenComparing(ApplicationInterview::id))
            .toList();

    Map<UUID, ApplicationInterview> lastByApplicationId = new HashMap<>();
    for (ApplicationInterview interview : sortedInterviews) {
      lastByApplicationId.putIfAbsent(interview.applicationId(), interview);
    }
    return lastByApplicationId;
  }
}
