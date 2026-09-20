package com.offertrack.auth;

import com.offertrack.auth.AuthSessionRepository.RefreshToken;
import com.offertrack.auth.AuthSessionRepository.Session;
import com.offertrack.users.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthSessionService {
  private final AuthSessionRepository sessions;
  private final UserRepository users;
  private final SessionProperties properties;
  private final JwtService jwt;
  private final Clock clock;
  private final TransactionTemplate transaction;

  public AuthSessionService(
      AuthSessionRepository sessions,
      UserRepository users,
      SessionProperties properties,
      JwtService jwt,
      Clock clock,
      PlatformTransactionManager manager) {
    this.sessions = sessions;
    this.users = users;
    this.properties = properties;
    this.jwt = jwt;
    this.clock = clock;
    this.transaction = new TransactionTemplate(manager);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setTimeout(10);
  }

  /** Caller holds the user lock; joins the login transaction, never commits independently. */
  @Transactional(propagation = Propagation.MANDATORY)
  public SessionTokens create(UUID userId) {
    List<Session> locked = sessions.lockUserSessions(userId);
    OffsetDateTime now = OffsetDateTime.now(clock);
    List<Session> active =
        locked.stream()
            .filter(session -> session.active(now))
            .sorted(
                Comparator.comparing(Session::lastRefreshedAt)
                    .thenComparing(Session::createdAt)
                    .thenComparing(Session::id))
            .toList();
    for (int index = 0; index <= active.size() - properties.getMaxActive(); index++) {
      sessions.revoke(active.get(index).id(), now, SessionRevocationReason.SESSION_LIMIT);
    }
    OffsetDateTime absolute = now.plus(properties.getAbsoluteTtl());
    OffsetDateTime expiry = earlier(now.plus(properties.getInactivityTtl()), absolute);
    Session session = new Session(UUID.randomUUID(), userId, now, now, expiry, absolute, null);
    sessions.create(session);
    return issue(session, now, expiry);
  }

  public boolean authenticates(JwtService.AccessClaims claims) {
    return store(
        () ->
            sessions
                .find(claims.sessionId())
                .filter(session -> session.userId().equals(claims.userId()))
                .filter(session -> session.active(OffsetDateTime.now(clock)))
                .isPresent());
  }

  public Optional<SessionTokens> refresh(String rawToken) {
    if (!RefreshTokenCodec.isValidShape(rawToken)) return Optional.empty();
    // Rejection is a VALUE, not an exception: replay revocation must commit first.
    return store(() -> transaction.execute(status -> rotate(RefreshTokenCodec.hash(rawToken))));
  }

  private Optional<SessionTokens> rotate(byte[] hash) {
    RefreshToken discovered = sessions.findToken(hash).orElse(null);
    if (discovered == null) return Optional.empty();
    Session discoveredSession = sessions.find(discovered.sessionId()).orElse(null);
    if (discoveredSession == null || users.lockById(discoveredSession.userId()).isEmpty())
      return Optional.empty();
    Session session = sessions.lock(discovered.sessionId()).orElse(null);
    RefreshToken token = sessions.lockToken(discovered.id()).orElse(null);
    OffsetDateTime now = OffsetDateTime.now(clock);
    if (session == null || token == null || !token.sessionId().equals(session.id()))
      return Optional.empty();
    if (token.consumedAt() != null) {
      sessions.revoke(session.id(), now, SessionRevocationReason.REFRESH_REPLAY);
      return Optional.empty();
    }
    if (!session.active(now) || token.revokedAt() != null || !now.isBefore(token.expiresAt()))
      return Optional.empty();
    OffsetDateTime expiry =
        earlier(now.plus(properties.getInactivityTtl()), session.absoluteExpiresAt());
    sessions.consume(token.id(), now);
    SessionTokens result = issue(session, now, expiry);
    sessions.refreshed(session.id(), now, expiry);
    return Optional.of(result);
  }

  private SessionTokens issue(Session session, OffsetDateTime now, OffsetDateTime expiry) {
    String refresh = RefreshTokenCodec.generate();
    sessions.createToken(session.id(), RefreshTokenCodec.hash(refresh), now, expiry);
    JwtService.IssuedAccess access =
        jwt.generateAccessToken(session.userId(), session.id(), expiry.toInstant());
    return new SessionTokens(access.token(), refresh, access.expiresAt(), expiry.toInstant());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void revokeAllLocked(UUID userId, SessionRevocationReason reason) {
    List<Session> locked = sessions.lockUserSessions(userId);
    OffsetDateTime now = OffsetDateTime.now(clock);
    for (Session session : locked) sessions.revoke(session.id(), now, reason);
  }

  public void logoutAll(CurrentUser user) {
    store(
        () ->
            transaction.execute(
                status -> {
                  if (users.lockById(user.id()).isEmpty())
                    throw new AuthenticationRequiredException();
                  List<Session> locked = sessions.lockUserSessions(user.id());
                  OffsetDateTime now = OffsetDateTime.now(clock);
                  if (locked.stream()
                      .noneMatch(
                          session ->
                              session.id().equals(user.sessionId()) && session.active(now))) {
                    throw new AuthenticationRequiredException();
                  }
                  for (Session session : locked)
                    sessions.revoke(session.id(), now, SessionRevocationReason.LOGOUT_ALL);
                  return null;
                }));
  }

  public void logout(String accessToken, String refreshToken) {
    store(
        () ->
            transaction.execute(
                status -> {
                  List<Session> candidates = new ArrayList<>();
                  jwt.verify(accessToken)
                      .ifPresent(
                          claims ->
                              sessions
                                  .find(claims.sessionId())
                                  .filter(session -> session.userId().equals(claims.userId()))
                                  .ifPresent(candidates::add));
                  Optional<RefreshToken> discovered =
                      RefreshTokenCodec.isValidShape(refreshToken)
                          ? sessions.findToken(RefreshTokenCodec.hash(refreshToken))
                          : Optional.empty();
                  discovered
                      .flatMap(token -> sessions.find(token.sessionId()))
                      .ifPresent(candidates::add);
                  List<UUID> userIds =
                      candidates.stream().map(Session::userId).distinct().sorted().toList();
                  for (UUID userId : userIds) users.lockById(userId);
                  List<Session> locked =
                      candidates.stream()
                          .map(Session::id)
                          .distinct()
                          .sorted()
                          .map(sessions::lock)
                          .flatMap(Optional::stream)
                          .toList();
                  RefreshToken token =
                      discovered.flatMap(value -> sessions.lockToken(value.id())).orElse(null);
                  OffsetDateTime now = OffsetDateTime.now(clock);
                  Optional<JwtService.AccessClaims> claims = jwt.verify(accessToken);
                  for (Session session : locked) {
                    boolean viaAccess =
                        claims
                            .filter(
                                value ->
                                    value.sessionId().equals(session.id())
                                        && value.userId().equals(session.userId()))
                            .isPresent();
                    boolean viaRefresh = token != null && token.sessionId().equals(session.id());
                    if (viaRefresh && token.consumedAt() != null) {
                      sessions.revoke(session.id(), now, SessionRevocationReason.REFRESH_REPLAY);
                    } else if (viaAccess
                        || (viaRefresh
                            && token.revokedAt() == null
                            && now.isBefore(token.expiresAt()))) {
                      sessions.revoke(session.id(), now, SessionRevocationReason.LOGOUT);
                    }
                  }
                  return null;
                }));
  }

  private static OffsetDateTime earlier(OffsetDateTime first, OffsetDateTime second) {
    return first.isBefore(second) ? first : second;
  }

  private static <T> T store(Supplier<T> work) {
    try {
      return work.get();
    } catch (org.jooq.exception.DataAccessException
        | org.springframework.dao.DataAccessException
        | org.springframework.transaction.TransactionException exception) {
      throw new AuthServiceUnavailableException();
    }
  }
}
