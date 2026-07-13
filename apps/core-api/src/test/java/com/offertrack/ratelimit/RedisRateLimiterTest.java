package com.offertrack.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {
  @Mock private StringRedisTemplate redisTemplate;

  private RedisRateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    RateLimitProperties properties = new RateLimitProperties();
    properties.setKeySecret("test-rate-limit-key-secret-1234567890");
    Clock clock = Clock.fixed(Instant.parse("2026-07-12T00:00:59Z"), ZoneOffset.UTC);
    rateLimiter =
        new RedisRateLimiter(redisTemplate, new RateLimitSubjectHasher(properties), clock);
  }

  @Test
  void allowsTheRequestAtTheConfiguredLimitAndClampsRetryAfterToOneSecond() {
    when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("1"))).thenReturn(5L);

    RateLimitDecision decision =
        rateLimiter.consume(
            new RateLimitAttempt(
                RateLimitPolicy.AI_USER_MINUTE,
                RateLimitSubjectType.USER,
                "11111111-1111-1111-1111-111111111111",
                new RateLimitProperties.Policy(5, Duration.ofMinutes(1))));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.retryAfterSeconds()).isEqualTo(1);
  }

  @Test
  void deniesAboveTheLimitAndUsesOnlyAHmacInTheRedisKey() {
    when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("1"))).thenReturn(6L);
    String rawSubject = "sensitive@example.com";

    RateLimitDecision decision =
        rateLimiter.consume(
            new RateLimitAttempt(
                RateLimitPolicy.LOGIN_EMAIL,
                RateLimitSubjectType.EMAIL,
                rawSubject,
                new RateLimitProperties.Policy(5, Duration.ofMinutes(1))));

    ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
    verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), eq("1"));
    assertThat(decision.allowed()).isFalse();
    assertThat(keys.getValue()).singleElement().asString().doesNotContain(rawSubject);
    assertThat(keys.getValue().getFirst())
        .startsWith("offertrack:rate-limit:v1:login-email:email:");
  }

  @Test
  void translatesSpringStorageFailuresWithoutLeakingTheirMessageOrCause() {
    when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("1")))
        .thenThrow(new DataAccessResourceFailureException("redis-password-marker"));

    assertThatThrownBy(() -> rateLimiter.consume(attempt()))
        .isInstanceOf(RateLimitStoreUnavailableException.class)
        .hasMessage("Rate-limit store is unavailable.")
        .hasNoCause()
        .hasMessageNotContaining("redis-password-marker");
  }

  @Test
  void translatesMissingRedisResultsToTheDomainFailure() {
    when(redisTemplate.execute(any(RedisScript.class), anyList(), eq("1"))).thenReturn(null);

    assertThatThrownBy(() -> rateLimiter.consume(attempt()))
        .isInstanceOf(RateLimitStoreUnavailableException.class)
        .hasNoCause();
  }

  private static RateLimitAttempt attempt() {
    return new RateLimitAttempt(
        RateLimitPolicy.AI_USER_MINUTE,
        RateLimitSubjectType.USER,
        "11111111-1111-1111-1111-111111111111",
        new RateLimitProperties.Policy(5, Duration.ofMinutes(1)));
  }
}
