package com.offertrack.ratelimit;

import static com.offertrack.jooq.generated.tables.RateLimitCounters.RATE_LIMIT_COUNTERS;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class PostgresRateLimiter implements RateLimiter {
  private static final Logger log = LoggerFactory.getLogger(PostgresRateLimiter.class);
  private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);
  private static final Duration BACKLOG_CLEANUP_INTERVAL = Duration.ofSeconds(1);
  private static final Duration CLEANUP_GRACE = Duration.ofMinutes(5);
  private static final int CLEANUP_BATCH_SIZE = 100;

  private final DSLContext dsl;
  private final RateLimitSubjectHasher subjectHasher;
  private final Clock clock;
  private final AtomicLong nextCleanupAt = new AtomicLong(Long.MIN_VALUE);

  PostgresRateLimiter(DSLContext dsl, RateLimitSubjectHasher subjectHasher, Clock clock) {
    // Keep database exception details out of logs even when jOOQ debug logging is enabled.
    this.dsl =
        dsl.configuration().deriveSettings(settings -> settings.withExecuteLogging(false)).dsl();
    this.subjectHasher = subjectHasher;
    this.clock = clock;
  }

  @Override
  public RateLimitDecision consume(RateLimitAttempt attempt) {
    long windowSeconds = Math.max(1, attempt.configuration().getWindow().toSeconds());
    long epochSeconds = clock.instant().getEpochSecond();
    long bucket = Math.floorDiv(epochSeconds, windowSeconds);
    long retryAfterSeconds =
        Math.max(1, windowSeconds - Math.floorMod(epochSeconds, windowSeconds));
    String subjectHash = subjectHasher.hash(attempt.subject());

    Long count;
    try {
      count =
          dsl.insertInto(RATE_LIMIT_COUNTERS)
              .set(RATE_LIMIT_COUNTERS.POLICY, attempt.policy().key())
              .set(RATE_LIMIT_COUNTERS.SUBJECT_TYPE, attempt.subjectType().key())
              .set(RATE_LIMIT_COUNTERS.SUBJECT_HASH, subjectHash)
              .set(RATE_LIMIT_COUNTERS.BUCKET, bucket)
              .set(RATE_LIMIT_COUNTERS.REQUEST_COUNT, 1L)
              .set(
                  RATE_LIMIT_COUNTERS.EXPIRES_AT,
                  Instant.ofEpochSecond(epochSeconds + retryAfterSeconds).atOffset(ZoneOffset.UTC))
              .onConflict(
                  RATE_LIMIT_COUNTERS.POLICY,
                  RATE_LIMIT_COUNTERS.SUBJECT_TYPE,
                  RATE_LIMIT_COUNTERS.SUBJECT_HASH,
                  RATE_LIMIT_COUNTERS.BUCKET)
              .doUpdate()
              .set(RATE_LIMIT_COUNTERS.REQUEST_COUNT, RATE_LIMIT_COUNTERS.REQUEST_COUNT.plus(1L))
              .returningResult(RATE_LIMIT_COUNTERS.REQUEST_COUNT)
              .fetchOne(RATE_LIMIT_COUNTERS.REQUEST_COUNT);
    } catch (org.jooq.exception.DataAccessException
        | org.springframework.dao.DataAccessException exception) {
      throw new RateLimitStoreUnavailableException();
    }

    if (count == null) {
      throw new RateLimitStoreUnavailableException();
    }

    // Controller guards run before business transactions. These are separate
    // autocommit statements so cleanup cannot roll back a consumed attempt.
    cleanupIfDue();
    return new RateLimitDecision(
        count <= attempt.configuration().getMaxAttempts(), retryAfterSeconds);
  }

  private void cleanupIfDue() {
    long now = clock.millis();
    long next = nextCleanupAt.get();
    long claimedNextCleanupAt = now + CLEANUP_INTERVAL.toMillis();
    if (now < next || !nextCleanupAt.compareAndSet(next, claimedNextCleanupAt)) {
      return;
    }

    try {
      int deleted =
          dsl.deleteFrom(RATE_LIMIT_COUNTERS)
              .where(
                  DSL.row(
                          RATE_LIMIT_COUNTERS.POLICY,
                          RATE_LIMIT_COUNTERS.SUBJECT_TYPE,
                          RATE_LIMIT_COUNTERS.SUBJECT_HASH,
                          RATE_LIMIT_COUNTERS.BUCKET)
                      .in(
                          dsl.select(
                                  RATE_LIMIT_COUNTERS.POLICY,
                                  RATE_LIMIT_COUNTERS.SUBJECT_TYPE,
                                  RATE_LIMIT_COUNTERS.SUBJECT_HASH,
                                  RATE_LIMIT_COUNTERS.BUCKET)
                              .from(RATE_LIMIT_COUNTERS)
                              .where(
                                  RATE_LIMIT_COUNTERS.EXPIRES_AT.lt(
                                      Instant.ofEpochMilli(now)
                                          .minus(CLEANUP_GRACE)
                                          .atOffset(ZoneOffset.UTC)))
                              .orderBy(RATE_LIMIT_COUNTERS.EXPIRES_AT)
                              .limit(CLEANUP_BATCH_SIZE)
                              .forUpdate()
                              .skipLocked()))
              .execute();
      if (deleted == CLEANUP_BATCH_SIZE) {
        // Do not replace a newer claim if this cleanup took longer than the normal interval.
        nextCleanupAt.compareAndSet(
            claimedNextCleanupAt, clock.millis() + BACKLOG_CLEANUP_INTERVAL.toMillis());
      }
    } catch (RuntimeException exception) {
      log.warn("rate_limit_cleanup_failed");
    }
  }
}
