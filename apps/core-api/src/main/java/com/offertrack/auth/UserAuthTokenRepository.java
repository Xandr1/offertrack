package com.offertrack.auth;

import static com.offertrack.jooq.generated.tables.UserAuthTokens.USER_AUTH_TOKENS;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class UserAuthTokenRepository {
  private final DSLContext dsl;

  public UserAuthTokenRepository(DSLContext dsl) {
    this.dsl = dsl.configuration().deriveSettings(s -> s.withExecuteLogging(false)).dsl();
  }

  public void create(
      UUID userId,
      AuthTokenPurpose purpose,
      String tokenHash,
      OffsetDateTime expiresAt,
      OffsetDateTime createdAt) {
    int inserted =
        dsl.insertInto(USER_AUTH_TOKENS)
            .set(USER_AUTH_TOKENS.ID, UUID.randomUUID())
            .set(USER_AUTH_TOKENS.USER_ID, userId)
            .set(USER_AUTH_TOKENS.PURPOSE, purpose.value())
            .set(USER_AUTH_TOKENS.TOKEN_HASH, tokenHash)
            .set(USER_AUTH_TOKENS.EXPIRES_AT, expiresAt)
            .set(USER_AUTH_TOKENS.CREATED_AT, createdAt)
            .onConflictDoNothing()
            .execute();
    if (inserted != 1) throw new AuthServiceUnavailableException();
  }

  public Optional<UUID> consumeActiveToken(
      AuthTokenPurpose purpose, String tokenHash, OffsetDateTime consumedAt) {
    return dsl.update(USER_AUTH_TOKENS)
        .set(USER_AUTH_TOKENS.CONSUMED_AT, consumedAt)
        .where(USER_AUTH_TOKENS.PURPOSE.eq(purpose.value()))
        .and(USER_AUTH_TOKENS.TOKEN_HASH.eq(tokenHash))
        .and(USER_AUTH_TOKENS.CONSUMED_AT.isNull())
        .and(USER_AUTH_TOKENS.EXPIRES_AT.gt(consumedAt))
        .returning(USER_AUTH_TOKENS.USER_ID)
        .fetchOptional(record -> record.get(USER_AUTH_TOKENS.USER_ID));
  }

  public Optional<UUID> findUser(AuthTokenPurpose purpose, String hash) {
    return dsl.select(USER_AUTH_TOKENS.USER_ID)
        .from(USER_AUTH_TOKENS)
        .where(USER_AUTH_TOKENS.PURPOSE.eq(purpose.value()))
        .and(USER_AUTH_TOKENS.TOKEN_HASH.eq(hash))
        .fetchOptional(USER_AUTH_TOKENS.USER_ID);
  }

  public void consumeAllForUser(UUID userId, OffsetDateTime now) {
    dsl.update(USER_AUTH_TOKENS)
        .set(USER_AUTH_TOKENS.CONSUMED_AT, now)
        .where(USER_AUTH_TOKENS.USER_ID.eq(userId))
        .and(USER_AUTH_TOKENS.CONSUMED_AT.isNull())
        .execute();
  }

  public int consumeActiveTokensForUser(
      AuthTokenPurpose purpose, UUID userId, OffsetDateTime consumedAt) {
    return dsl.update(USER_AUTH_TOKENS)
        .set(USER_AUTH_TOKENS.CONSUMED_AT, consumedAt)
        .where(USER_AUTH_TOKENS.PURPOSE.eq(purpose.value()))
        .and(USER_AUTH_TOKENS.USER_ID.eq(userId))
        .and(USER_AUTH_TOKENS.CONSUMED_AT.isNull())
        .and(USER_AUTH_TOKENS.EXPIRES_AT.gt(consumedAt))
        .execute();
  }
}
