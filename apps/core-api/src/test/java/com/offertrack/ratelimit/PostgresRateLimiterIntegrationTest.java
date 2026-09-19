package com.offertrack.ratelimit;

import static com.offertrack.jooq.generated.tables.RateLimitCounters.RATE_LIMIT_COUNTERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PostgresRateLimiterIntegrationTest {
  private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
          DockerImageName.parse(
                  "postgres:16.14-bookworm@sha256:c95fd5346040eba2de3c435e14874af18f5d681fb5848d4f081dbead0878af28")
              .asCompatibleSubstituteFor("postgres"));

  private static DSLContext dsl;
  private static DataSource dataSource;
  private final RateLimitProperties properties = new RateLimitProperties();
  private RateLimitSubjectHasher hasher;

  @BeforeAll
  static void migrate() {
    dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
    dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    dsl.execute("create role counter_only login password 'test-only-counter-password'");
    dsl.execute("grant usage on schema public to counter_only");
    dsl.execute("grant select, insert, update on rate_limit_counters to counter_only");
  }

  @BeforeEach
  void reset() {
    dsl.deleteFrom(RATE_LIMIT_COUNTERS).execute();
    properties.setKeySecret("test-rate-limit-key-secret-1234567890");
    hasher = new RateLimitSubjectHasher(properties);
  }

  @Test
  void firstRequestIsOneRepeatedRequestsIncrementAndNewBucketStartsAtOne() {
    PostgresRateLimiter limiter = limiter(NOW);
    RateLimitAttempt attempt = attempt("person@example.com", 2);
    assertThat(limiter.consume(attempt)).isEqualTo(new RateLimitDecision(true, 60));
    assertThat(count(attempt, NOW)).isEqualTo(1);
    assertThat(limiter.consume(attempt).allowed()).isTrue();
    assertThat(count(attempt, NOW)).isEqualTo(2);
    assertThat(limiter.consume(attempt).allowed()).isFalse();
    assertThat(count(attempt, NOW)).isEqualTo(3);
    Instant nextBucket = NOW.plusSeconds(60);
    assertThat(limiter(nextBucket).consume(attempt).allowed()).isTrue();
    assertThat(count(attempt, nextBucket)).isEqualTo(1);
    assertThat(count(attempt, NOW)).isEqualTo(3);
  }

  @ParameterizedTest
  @EnumSource(RateLimitSubjectType.class)
  void persistsOnlyHmacDerivedSubjectMaterial(RateLimitSubjectType type) {
    String raw =
        switch (type) {
          case EMAIL -> "sensitive@example.com";
          case IP -> "203.0.113.42";
          case TOKEN -> "raw-password-reset-token-marker";
          case USER -> "11111111-1111-1111-1111-111111111111";
        };
    RateLimitAttempt attempt =
        new RateLimitAttempt(
            RateLimitPolicy.LOGIN_EMAIL,
            type,
            raw,
            new RateLimitProperties.Policy(2, Duration.ofMinutes(1)));
    limiter(NOW).consume(attempt);
    var row = dsl.selectFrom(RATE_LIMIT_COUNTERS).fetchSingle();
    assertThat(row.getSubjectHash()).isEqualTo(hasher.hash(raw)).matches("[0-9a-f]{64}");
    assertThat(row.toString()).doesNotContain(raw, properties.getKeySecret());
    assertThat(row.getExpiresAt().toInstant()).isEqualTo(NOW.plusSeconds(60));
  }

  @Test
  void policiesAndSubjectTypesHaveIndependentCounters() {
    PostgresRateLimiter limiter = limiter(NOW);
    var configuration = new RateLimitProperties.Policy(1, Duration.ofMinutes(1));
    for (RateLimitPolicy policy : RateLimitPolicy.values()) {
      for (RateLimitSubjectType type : RateLimitSubjectType.values()) {
        var attempt = new RateLimitAttempt(policy, type, "same-subject", configuration);
        assertThat(limiter.consume(attempt).allowed()).isTrue();
        assertThat(limiter.consume(attempt).allowed()).isFalse();
      }
    }
    assertThat(dsl.fetchCount(RATE_LIMIT_COUNTERS))
        .isEqualTo(RateLimitPolicy.values().length * RateLimitSubjectType.values().length);
  }

  @Test
  void concurrentInstancesNeverLoseUpdatesOrAllowMoreThanTheLimit() throws Exception {
    List<PostgresRateLimiter> instances = List.of(limiter(NOW), limiter(NOW));
    RateLimitAttempt attempt = attempt("concurrent@example.com", 7);
    List<Callable<RateLimitDecision>> calls = new ArrayList<>();
    for (int i = 0; i < 64; i++) {
      PostgresRateLimiter instance = instances.get(i % instances.size());
      calls.add(() -> instance.consume(attempt));
    }
    assertThat(runConcurrent(calls).stream().filter(RateLimitDecision::allowed).count())
        .isEqualTo(7);
    assertThat(count(attempt, NOW)).isEqualTo(64);
  }

  @Test
  void overlappingBucketsAndDelayedOldRequestsCannotResetTheNewCounter() throws Exception {
    Instant previous = NOW.minusSeconds(1);
    PostgresRateLimiter older = limiter(previous);
    PostgresRateLimiter newer = limiter(NOW);
    RateLimitAttempt attempt = attempt("overlap@example.com", 7);
    List<Callable<RateLimitDecision>> calls = new ArrayList<>();
    for (int i = 0; i < 32; i++) {
      calls.add(() -> older.consume(attempt));
      calls.add(() -> newer.consume(attempt));
    }
    assertThat(runConcurrent(calls).stream().filter(RateLimitDecision::allowed).count())
        .isEqualTo(14);
    assertThat(count(attempt, previous)).isEqualTo(32);
    assertThat(count(attempt, NOW)).isEqualTo(32);
    assertThat(older.consume(attempt)).isEqualTo(new RateLimitDecision(false, 1));
    assertThat(count(attempt, NOW)).isEqualTo(32);
  }

  @Test
  void cleanupIsBoundedSkipsLockedRowsAndKeepsRecentExpiry() throws Exception {
    for (int i = 0; i < 201; i++) {
      seed("stale", "old-" + i, NOW.minusSeconds(600 + i));
    }
    seed("recent", "at-grace-boundary", NOW.minusSeconds(300));
    seed("active", "active", NOW.plusSeconds(60));
    String lockedHash = hasher.hash("old-200");
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      DSL.using(connection, SQLDialect.POSTGRES)
          .selectFrom(RATE_LIMIT_COUNTERS)
          .where(RATE_LIMIT_COUNTERS.SUBJECT_HASH.eq(lockedHash))
          .forUpdate()
          .fetch();
      PostgresRateLimiter limiter = limiter(NOW);
      limiter.consume(attempt("cleanup@example.com", 1));
      assertThat(rows("stale")).isEqualTo(101);
      assertThat(rows("recent")).isEqualTo(1);
      assertThat(rows("active")).isEqualTo(1);
      assertThat(
              dsl.fetchExists(RATE_LIMIT_COUNTERS, RATE_LIMIT_COUNTERS.SUBJECT_HASH.eq(lockedHash)))
          .isTrue();
      assertThat(limiter.consume(attempt("cleanup@example.com", 1)).allowed()).isFalse();
      assertThat(rows("stale")).isEqualTo(101);
      connection.rollback();
    }
    limiter(NOW.plusSeconds(60)).consume(attempt("cleanup@example.com", 1));
    assertThat(rows("stale")).isEqualTo(1);
  }

  @Test
  void cleanupPermissionFailureCannotRollBackOrChangeCounterDecisions() {
    DSLContext counterOnly =
        DSL.using(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), "counter_only", "test-only-counter-password"),
            SQLDialect.POSTGRES);
    PostgresRateLimiter limiter =
        new PostgresRateLimiter(counterOnly, hasher, Clock.fixed(NOW, ZoneOffset.UTC));
    RateLimitAttempt attempt = attempt("cleanup-failure@example.com", 1);
    assertThat(limiter.consume(attempt).allowed()).isTrue();
    assertThat(limiter.consume(attempt).allowed()).isFalse();
    assertThat(count(attempt, NOW)).isEqualTo(2);
  }

  @Test
  void postgresConnectionFailureIsSanitizedAndFailsClosed() {
    DSLContext unavailable =
        DSL.using(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), "missing_role", "database-secret-marker"),
            SQLDialect.POSTGRES);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    PostgresRateLimiter limiter = new PostgresRateLimiter(unavailable, hasher, clock);
    assertThatThrownBy(() -> limiter.consume(attempt("user@example.com", 1)))
        .isInstanceOf(RateLimitStoreUnavailableException.class)
        .hasNoCause()
        .hasMessage("Rate-limit store is unavailable.");
    properties.setFailOpen(false);
    assertThatThrownBy(
            () ->
                new RateLimitGuard(limiter, properties, clock)
                    .checkLogin("user@example.com", "203.0.113.10"))
        .isInstanceOf(RateLimitServiceUnavailableException.class);
  }

  private PostgresRateLimiter limiter(Instant now) {
    return new PostgresRateLimiter(dsl, hasher, Clock.fixed(now, ZoneOffset.UTC));
  }

  private static RateLimitAttempt attempt(String subject, int limit) {
    return new RateLimitAttempt(
        RateLimitPolicy.LOGIN_EMAIL,
        RateLimitSubjectType.EMAIL,
        subject,
        new RateLimitProperties.Policy(limit, Duration.ofMinutes(1)));
  }

  private long count(RateLimitAttempt attempt, Instant instant) {
    return dsl.select(RATE_LIMIT_COUNTERS.REQUEST_COUNT)
        .from(RATE_LIMIT_COUNTERS)
        .where(RATE_LIMIT_COUNTERS.POLICY.eq(attempt.policy().key()))
        .and(RATE_LIMIT_COUNTERS.SUBJECT_TYPE.eq(attempt.subjectType().key()))
        .and(RATE_LIMIT_COUNTERS.SUBJECT_HASH.eq(hasher.hash(attempt.subject())))
        .and(
            RATE_LIMIT_COUNTERS.BUCKET.eq(
                Math.floorDiv(
                    instant.getEpochSecond(), attempt.configuration().getWindow().toSeconds())))
        .fetchSingle(RATE_LIMIT_COUNTERS.REQUEST_COUNT);
  }

  private void seed(String policy, String subject, Instant expiresAt) {
    dsl.insertInto(RATE_LIMIT_COUNTERS)
        .set(RATE_LIMIT_COUNTERS.POLICY, policy)
        .set(RATE_LIMIT_COUNTERS.SUBJECT_TYPE, "email")
        .set(RATE_LIMIT_COUNTERS.SUBJECT_HASH, hasher.hash(subject))
        .set(RATE_LIMIT_COUNTERS.BUCKET, 0L)
        .set(RATE_LIMIT_COUNTERS.REQUEST_COUNT, 1L)
        .set(RATE_LIMIT_COUNTERS.EXPIRES_AT, expiresAt.atOffset(ZoneOffset.UTC))
        .execute();
  }

  private int rows(String policy) {
    return dsl.fetchCount(RATE_LIMIT_COUNTERS, RATE_LIMIT_COUNTERS.POLICY.eq(policy));
  }

  private static List<RateLimitDecision> runConcurrent(List<Callable<RateLimitDecision>> calls)
      throws Exception {
    try (var executor = Executors.newFixedThreadPool(8)) {
      List<RateLimitDecision> decisions = new ArrayList<>();
      for (var future : executor.invokeAll(calls)) {
        decisions.add(future.get());
      }
      return decisions;
    }
  }
}
