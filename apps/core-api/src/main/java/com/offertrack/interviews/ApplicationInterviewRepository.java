package com.offertrack.interviews;

import static com.offertrack.jooq.generated.tables.ApplicationInterviews.APPLICATION_INTERVIEWS;

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
  private static final List<String> TERMINAL_NEXT_INTERVIEW_STATUSES =
      List.of(InterviewStatus.PASSED.value(), InterviewStatus.REJECTED.value());

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

  public int countByApplicationForUser(UUID applicationId, UUID userId) {
    return dsl.fetchCount(
        APPLICATION_INTERVIEWS,
        APPLICATION_INTERVIEWS
            .APPLICATION_ID
            .eq(applicationId)
            .and(APPLICATION_INTERVIEWS.USER_ID.eq(userId)));
  }

  public Optional<ApplicationInterview> findNextByApplicationForUser(
      UUID applicationId, UUID userId) {
    return dsl.selectFrom(APPLICATION_INTERVIEWS)
        .where(APPLICATION_INTERVIEWS.APPLICATION_ID.eq(applicationId))
        .and(APPLICATION_INTERVIEWS.USER_ID.eq(userId))
        .and(APPLICATION_INTERVIEWS.STATUS.notIn(TERMINAL_NEXT_INTERVIEW_STATUSES))
        .orderBy(
            APPLICATION_INTERVIEWS.SCHEDULED_AT.asc().nullsLast(),
            APPLICATION_INTERVIEWS.CREATED_AT.asc())
        .limit(1)
        .fetchOptional(ApplicationInterviewMapper::fromRecord);
  }

  public Map<UUID, ApplicationInterview> findNextByApplicationIdsForUser(
      UUID userId, List<UUID> applicationIds) {
    if (applicationIds.isEmpty()) {
      return Map.of();
    }

    List<ApplicationInterview> interviews =
        dsl.selectFrom(APPLICATION_INTERVIEWS)
            .where(APPLICATION_INTERVIEWS.USER_ID.eq(userId))
            .and(APPLICATION_INTERVIEWS.APPLICATION_ID.in(applicationIds))
            .and(APPLICATION_INTERVIEWS.STATUS.notIn(TERMINAL_NEXT_INTERVIEW_STATUSES))
            .orderBy(
                APPLICATION_INTERVIEWS.APPLICATION_ID.asc(),
                APPLICATION_INTERVIEWS.SCHEDULED_AT.asc().nullsLast(),
                APPLICATION_INTERVIEWS.CREATED_AT.asc())
            .fetch(ApplicationInterviewMapper::fromRecord);

    return selectNextInterviewsByApplicationId(interviews);
  }

  static Map<UUID, ApplicationInterview> selectNextInterviewsByApplicationId(
      List<ApplicationInterview> interviews) {
    List<ApplicationInterview> sortedInterviews =
        interviews.stream()
            .filter(interview -> !isTerminalForNextInterview(interview.status()))
            .sorted(
                Comparator.comparing(ApplicationInterview::applicationId)
                    .thenComparing(
                        ApplicationInterview::scheduledAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ApplicationInterview::createdAt))
            .toList();

    Map<UUID, ApplicationInterview> nextByApplicationId = new HashMap<>();

    for (ApplicationInterview interview : sortedInterviews) {
      nextByApplicationId.putIfAbsent(interview.applicationId(), interview);
    }

    return nextByApplicationId;
  }

  private static boolean isTerminalForNextInterview(InterviewStatus status) {
    return status == InterviewStatus.PASSED || status == InterviewStatus.REJECTED;
  }
}
