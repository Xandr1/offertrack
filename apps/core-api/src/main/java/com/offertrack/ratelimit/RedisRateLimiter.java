package com.offertrack.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class RedisRateLimiter {
  private static final String KEY_PREFIX = "offertrack:rate-limit:v1";

  private final StringRedisTemplate redisTemplate;
  private final RateLimitSubjectHasher subjectHasher;
  private final Clock clock;
  private final DefaultRedisScript<Long> script;

  RedisRateLimiter(
      StringRedisTemplate redisTemplate, RateLimitSubjectHasher subjectHasher, Clock clock) {
    this.redisTemplate = redisTemplate;
    this.subjectHasher = subjectHasher;
    this.clock = clock;
    this.script = new DefaultRedisScript<>();
    this.script.setLocation(new ClassPathResource("redis/rate-limit.lua"));
    this.script.setResultType(Long.class);
  }

  RateLimitDecision consume(RateLimitAttempt attempt) {
    long windowSeconds = windowSeconds(attempt.configuration().getWindow());
    long epochSeconds = clock.instant().getEpochSecond();
    long bucket = Math.floorDiv(epochSeconds, windowSeconds);
    long retryAfterSeconds =
        Math.max(1, windowSeconds - Math.floorMod(epochSeconds, windowSeconds));
    String key = buildKey(attempt, bucket);

    Long count = redisTemplate.execute(script, List.of(key), Long.toString(retryAfterSeconds));
    if (count == null) {
      throw new DataAccessResourceFailureException("Rate-limit counter returned no result");
    }

    return new RateLimitDecision(
        count <= attempt.configuration().getMaxAttempts(), retryAfterSeconds);
  }

  private String buildKey(RateLimitAttempt attempt, long bucket) {
    return String.join(
        ":",
        KEY_PREFIX,
        attempt.policy().key(),
        attempt.subjectType().key(),
        subjectHasher.hash(attempt.subject()),
        Long.toString(bucket));
  }

  private static long windowSeconds(Duration window) {
    return Math.max(1, window.toSeconds());
  }
}
