package com.offertrack.ratelimit;

import static com.offertrack.jooq.generated.tables.RateLimitCounters.RATE_LIMIT_COUNTERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(OutputCaptureExtension.class)
class PostgresRateLimiterTest {
  private final Clock clock = mock(Clock.class);
  private final AtomicInteger cleanupCalls = new AtomicInteger();
  private final RateLimitProperties properties = new RateLimitProperties();
  private final Logger sqlLogger =
      (Logger) LoggerFactory.getLogger("org.jooq.tools.LoggerListener");
  private Level previousSqlLogLevel;
  private PostgresRateLimiter limiter;
  private Long count = 1L;
  private RuntimeException counterFailure;
  private boolean cleanupFails;

  @BeforeEach
  void setUp() {
    previousSqlLogLevel = sqlLogger.getLevel();
    sqlLogger.setLevel(Level.DEBUG);
    setTime("2026-07-12T00:00:59Z");
    properties.setKeySecret("test-rate-limit-key-secret-1234567890");
    DSLContext records = DSL.using(SQLDialect.POSTGRES);
    DSLContext dsl =
        DSL.using(
            new MockConnection(
                context -> {
                  if (context.sql().startsWith("delete")) {
                    cleanupCalls.incrementAndGet();
                    if (cleanupFails) {
                      throw new SQLException("cleanup-secret-marker");
                    }
                    return new MockResult[] {new MockResult(0)};
                  }
                  if (counterFailure != null) {
                    throw counterFailure;
                  }
                  var result = records.newResult(RATE_LIMIT_COUNTERS.REQUEST_COUNT);
                  if (count != null) {
                    result.add(records.newRecord(RATE_LIMIT_COUNTERS.REQUEST_COUNT).value1(count));
                  }
                  return new MockResult[] {new MockResult(result.size(), result)};
                }),
            SQLDialect.POSTGRES);
    limiter = new PostgresRateLimiter(dsl, new RateLimitSubjectHasher(properties), clock);
  }

  @AfterEach
  void restoreLogging() {
    sqlLogger.setLevel(previousSqlLogLevel);
  }

  @Test
  void allowsTheLimitDeniesAboveItAndPreservesRetryAfter() {
    count = 5L;
    assertThat(limiter.consume(attempt())).isEqualTo(new RateLimitDecision(true, 1));
    count = 6L;
    assertThat(limiter.consume(attempt())).isEqualTo(new RateLimitDecision(false, 1));
    setTime("2026-07-12T00:01:00Z");
    assertThat(limiter.consume(attempt()).retryAfterSeconds()).isEqualTo(60);
  }

  @Test
  void sanitizesBothJooqAndSpringCounterFailuresAndDoesNotCleanUp(CapturedOutput output) {
    for (RuntimeException failure :
        List.of(
            new org.jooq.exception.DataAccessException("database-secret-marker"),
            new DataAccessResourceFailureException("database-secret-marker"))) {
      counterFailure = failure;
      assertThatThrownBy(() -> limiter.consume(attempt()))
          .isInstanceOf(RateLimitStoreUnavailableException.class)
          .hasMessage("Rate-limit store is unavailable.")
          .hasNoCause();
    }
    assertThat(cleanupCalls).hasValue(0);
    assertThat(output.getAll()).doesNotContain("database-secret-marker");
  }

  @Test
  void missingCounterResultIsUnavailableAndDoesNotTriggerCleanup() {
    count = null;
    assertThatThrownBy(() -> limiter.consume(attempt()))
        .isInstanceOf(RateLimitStoreUnavailableException.class)
        .hasNoCause();
    assertThat(cleanupCalls).hasValue(0);
  }

  @Test
  void counterFailureUsesTheExistingFailClosedGuard() {
    counterFailure = new org.jooq.exception.DataAccessException("database-secret-marker");
    properties.setFailOpen(false);
    RateLimitGuard guard = new RateLimitGuard(limiter, properties, clock);
    assertThatThrownBy(() -> guard.checkLogin("user@example.com", "203.0.113.10"))
        .isInstanceOf(RateLimitServiceUnavailableException.class);
    assertThat(cleanupCalls).hasValue(0);
  }

  @Test
  void cleanupFailureNeverChangesAllowedOrDeniedDecisionsAndIsThrottled(CapturedOutput output) {
    cleanupFails = true;
    assertThat(limiter.consume(attempt()).allowed()).isTrue();
    count = 6L;
    assertThat(limiter.consume(attempt()).allowed()).isFalse();
    setTime("2026-07-12T00:01:58.999Z");
    limiter.consume(attempt());
    assertThat(cleanupCalls).hasValue(1);
    setTime("2026-07-12T00:01:59Z");
    assertThat(limiter.consume(attempt()).allowed()).isFalse();
    assertThat(cleanupCalls).hasValue(2);
    assertThat(output.getOut())
        .contains("rate_limit_cleanup_failed")
        .doesNotContain("cleanup-secret-marker");
  }

  @Test
  void concurrentSuccessesOnlyClaimOneCleanupPerInterval() throws Exception {
    try (var executor = Executors.newFixedThreadPool(8)) {
      List<Callable<RateLimitDecision>> calls = new ArrayList<>();
      for (int i = 0; i < 64; i++) {
        calls.add(() -> limiter.consume(attempt()));
      }
      for (var result : executor.invokeAll(calls)) {
        assertThat(result.get().allowed()).isTrue();
      }
    }
    assertThat(cleanupCalls).hasValue(1);
    setTime("2026-07-12T00:01:59Z");
    limiter.consume(attempt());
    assertThat(cleanupCalls).hasValue(2);
  }

  private void setTime(String value) {
    Instant instant = Instant.parse(value);
    when(clock.instant()).thenReturn(instant);
    when(clock.millis()).thenReturn(instant.toEpochMilli());
  }

  private static RateLimitAttempt attempt() {
    return new RateLimitAttempt(
        RateLimitPolicy.AI_USER_MINUTE,
        RateLimitSubjectType.USER,
        "11111111-1111-1111-1111-111111111111",
        new RateLimitProperties.Policy(5, Duration.ofMinutes(1)));
  }
}
