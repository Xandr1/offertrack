package com.offertrack.auth;

import static com.offertrack.jooq.generated.tables.UserIdentities.USER_IDENTITIES;

import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class UserIdentityRepository {
  private final DSLContext dsl;

  public UserIdentityRepository(DSLContext dsl) {
    this.dsl = dsl.configuration().deriveSettings(s -> s.withExecuteLogging(false)).dsl();
  }

  public void lockGoogleSubject(String subject) {
    long key = ByteBuffer.wrap(RefreshTokenCodec.hash("google:" + subject)).getLong();
    dsl.fetch("select pg_advisory_xact_lock(?)", key);
  }

  public Optional<UUID> findGoogleUser(String subject) {
    return dsl.select(USER_IDENTITIES.USER_ID)
        .from(USER_IDENTITIES)
        .where(USER_IDENTITIES.PROVIDER.eq("google"))
        .and(USER_IDENTITIES.PROVIDER_SUBJECT.eq(subject))
        .fetchOptional(USER_IDENTITIES.USER_ID);
  }

  public boolean hasGoogleIdentity(UUID userId) {
    return dsl.fetchExists(
        dsl.selectOne()
            .from(USER_IDENTITIES)
            .where(USER_IDENTITIES.USER_ID.eq(userId))
            .and(USER_IDENTITIES.PROVIDER.eq("google")));
  }

  public boolean linkGoogle(UUID userId, String subject, OffsetDateTime now) {
    return dsl.insertInto(USER_IDENTITIES)
            .set(USER_IDENTITIES.USER_ID, userId)
            .set(USER_IDENTITIES.PROVIDER, "google")
            .set(USER_IDENTITIES.PROVIDER_SUBJECT, subject)
            .set(USER_IDENTITIES.CREATED_AT, now)
            .set(USER_IDENTITIES.UPDATED_AT, now)
            .onConflictDoNothing()
            .execute()
        == 1;
  }
}
