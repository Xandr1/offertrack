package com.offertrack.auth;

import static com.offertrack.jooq.generated.tables.AuthRefreshTokens.AUTH_REFRESH_TOKENS;
import static com.offertrack.jooq.generated.tables.AuthSessions.AUTH_SESSIONS;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class AuthSessionRepository {
  private final DSLContext dsl;

  public AuthSessionRepository(DSLContext dsl) {
    this.dsl = dsl.configuration().deriveSettings(s -> s.withExecuteLogging(false)).dsl();
  }

  public Optional<Session> find(UUID id) {
    return dsl.selectFrom(AUTH_SESSIONS)
        .where(AUTH_SESSIONS.ID.eq(id))
        .fetchOptional(this::session);
  }

  public Optional<Session> lock(UUID id) {
    return dsl.selectFrom(AUTH_SESSIONS)
        .where(AUTH_SESSIONS.ID.eq(id))
        .forUpdate()
        .fetchOptional(this::session);
  }

  public List<Session> lockUserSessions(UUID userId) {
    return dsl.selectFrom(AUTH_SESSIONS)
        .where(AUTH_SESSIONS.USER_ID.eq(userId))
        .and(AUTH_SESSIONS.REVOKED_AT.isNull())
        .orderBy(AUTH_SESSIONS.ID)
        .forUpdate()
        .fetch(this::session);
  }

  public void create(Session session) {
    int inserted =
        dsl.insertInto(AUTH_SESSIONS)
            .set(AUTH_SESSIONS.ID, session.id())
            .set(AUTH_SESSIONS.USER_ID, session.userId())
            .set(AUTH_SESSIONS.CREATED_AT, session.createdAt())
            .set(AUTH_SESSIONS.LAST_REFRESHED_AT, session.lastRefreshedAt())
            .set(AUTH_SESSIONS.INACTIVITY_EXPIRES_AT, session.inactivityExpiresAt())
            .set(AUTH_SESSIONS.ABSOLUTE_EXPIRES_AT, session.absoluteExpiresAt())
            .onConflictDoNothing()
            .execute();
    if (inserted != 1) throw new AuthServiceUnavailableException();
  }

  public void revoke(UUID id, OffsetDateTime now, SessionRevocationReason reason) {
    dsl.update(AUTH_SESSIONS)
        .set(AUTH_SESSIONS.REVOKED_AT, now)
        .set(AUTH_SESSIONS.REVOCATION_REASON, reason.value())
        .where(AUTH_SESSIONS.ID.eq(id))
        .and(AUTH_SESSIONS.REVOKED_AT.isNull())
        .execute();
    dsl.update(AUTH_REFRESH_TOKENS)
        .set(AUTH_REFRESH_TOKENS.REVOKED_AT, now)
        .where(AUTH_REFRESH_TOKENS.SESSION_ID.eq(id))
        .and(AUTH_REFRESH_TOKENS.REVOKED_AT.isNull())
        .and(AUTH_REFRESH_TOKENS.CONSUMED_AT.isNull())
        .execute();
  }

  public Optional<RefreshToken> findToken(byte[] hash) {
    return dsl.selectFrom(AUTH_REFRESH_TOKENS)
        .where(AUTH_REFRESH_TOKENS.TOKEN_HASH.eq(hash))
        .fetchOptional(this::token);
  }

  public Optional<RefreshToken> lockToken(UUID id) {
    return dsl.selectFrom(AUTH_REFRESH_TOKENS)
        .where(AUTH_REFRESH_TOKENS.ID.eq(id))
        .forUpdate()
        .fetchOptional(this::token);
  }

  public void createToken(
      UUID sessionId, byte[] hash, OffsetDateTime now, OffsetDateTime expiresAt) {
    int inserted =
        dsl.insertInto(AUTH_REFRESH_TOKENS)
            .set(AUTH_REFRESH_TOKENS.ID, UUID.randomUUID())
            .set(AUTH_REFRESH_TOKENS.SESSION_ID, sessionId)
            .set(AUTH_REFRESH_TOKENS.TOKEN_HASH, hash)
            .set(AUTH_REFRESH_TOKENS.ISSUED_AT, now)
            .set(AUTH_REFRESH_TOKENS.EXPIRES_AT, expiresAt)
            .onConflictDoNothing()
            .execute();
    if (inserted != 1) throw new AuthServiceUnavailableException();
  }

  public void consume(UUID id, OffsetDateTime now) {
    dsl.update(AUTH_REFRESH_TOKENS)
        .set(AUTH_REFRESH_TOKENS.CONSUMED_AT, now)
        .where(AUTH_REFRESH_TOKENS.ID.eq(id))
        .execute();
  }

  public void refreshed(UUID id, OffsetDateTime now, OffsetDateTime inactivityExpiry) {
    dsl.update(AUTH_SESSIONS)
        .set(AUTH_SESSIONS.LAST_REFRESHED_AT, now)
        .set(AUTH_SESSIONS.INACTIVITY_EXPIRES_AT, inactivityExpiry)
        .where(AUTH_SESSIONS.ID.eq(id))
        .execute();
  }

  private Session session(org.jooq.Record row) {
    return new Session(
        row.get(AUTH_SESSIONS.ID),
        row.get(AUTH_SESSIONS.USER_ID),
        row.get(AUTH_SESSIONS.CREATED_AT),
        row.get(AUTH_SESSIONS.LAST_REFRESHED_AT),
        row.get(AUTH_SESSIONS.INACTIVITY_EXPIRES_AT),
        row.get(AUTH_SESSIONS.ABSOLUTE_EXPIRES_AT),
        row.get(AUTH_SESSIONS.REVOKED_AT));
  }

  private RefreshToken token(org.jooq.Record row) {
    return new RefreshToken(
        row.get(AUTH_REFRESH_TOKENS.ID),
        row.get(AUTH_REFRESH_TOKENS.SESSION_ID),
        row.get(AUTH_REFRESH_TOKENS.EXPIRES_AT),
        row.get(AUTH_REFRESH_TOKENS.CONSUMED_AT),
        row.get(AUTH_REFRESH_TOKENS.REVOKED_AT));
  }

  public record Session(
      UUID id,
      UUID userId,
      OffsetDateTime createdAt,
      OffsetDateTime lastRefreshedAt,
      OffsetDateTime inactivityExpiresAt,
      OffsetDateTime absoluteExpiresAt,
      OffsetDateTime revokedAt) {
    public boolean active(OffsetDateTime now) {
      return revokedAt == null
          && now.isBefore(inactivityExpiresAt)
          && now.isBefore(absoluteExpiresAt);
    }

    @Override
    public String toString() {
      return "Session[redacted]";
    }
  }

  public record RefreshToken(
      UUID id,
      UUID sessionId,
      OffsetDateTime expiresAt,
      OffsetDateTime consumedAt,
      OffsetDateTime revokedAt) {
    @Override
    public String toString() {
      return "RefreshToken[redacted]";
    }
  }
}
