package com.offertrack.settings;

import static com.offertrack.jooq.generated.tables.UserSettings.USER_SETTINGS;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class UserSettingsRepository {
  private final DSLContext dsl;

  public UserSettingsRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public Optional<UserSettings> findByUserId(UUID userId) {
    return dsl.selectFrom(USER_SETTINGS)
        .where(USER_SETTINGS.USER_ID.eq(userId))
        .fetchOptional(UserSettingsRepository::toDomain);
  }

  public UserSettings upsert(UserSettings settings) {
    OffsetDateTime now = OffsetDateTime.now();

    return dsl.insertInto(USER_SETTINGS)
        .set(USER_SETTINGS.USER_ID, settings.userId())
        .set(USER_SETTINGS.FOLLOW_UP_AFTER_APPLYING_DAYS, settings.followUpAfterApplyingDays())
        .set(USER_SETTINGS.UPCOMING_INTERVIEW_DAYS, settings.upcomingInterviewDays())
        .set(USER_SETTINGS.FOLLOW_UP_AFTER_INTERVIEW_DAYS, settings.followUpAfterInterviewDays())
        .set(USER_SETTINGS.TARGET_ROLE, settings.targetRole())
        .onConflict(USER_SETTINGS.USER_ID)
        .doUpdate()
        .set(USER_SETTINGS.FOLLOW_UP_AFTER_APPLYING_DAYS, settings.followUpAfterApplyingDays())
        .set(USER_SETTINGS.UPCOMING_INTERVIEW_DAYS, settings.upcomingInterviewDays())
        .set(USER_SETTINGS.FOLLOW_UP_AFTER_INTERVIEW_DAYS, settings.followUpAfterInterviewDays())
        .set(USER_SETTINGS.TARGET_ROLE, settings.targetRole())
        .set(USER_SETTINGS.UPDATED_AT, now)
        .returning()
        .fetchSingle(UserSettingsRepository::toDomain);
  }

  private static UserSettings toDomain(
      com.offertrack.jooq.generated.tables.records.UserSettingsRecord record) {
    return new UserSettings(
        record.getUserId(),
        record.getFollowUpAfterApplyingDays(),
        record.getUpcomingInterviewDays(),
        record.getFollowUpAfterInterviewDays(),
        record.getTargetRole());
  }
}
