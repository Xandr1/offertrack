package com.offertrack.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisRateLimiterIntegrationTest {
  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;

  @BeforeAll
  static void connect() {
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
  }

  @AfterAll
  static void disconnect() {
    connectionFactory.destroy();
  }

  @Test
  void atomicallyCountsExpiresAndSeparatesSubjectsWithoutRawKeyMaterial() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    MutableClock clock = new MutableClock(Instant.parse("2026-07-12T12:00:00Z"));
    RateLimitProperties properties = new RateLimitProperties();
    properties.setKeySecret("test-rate-limit-key-secret-1234567890");
    RedisRateLimiter limiter =
        new RedisRateLimiter(redisTemplate, new RateLimitSubjectHasher(properties), clock);
    RateLimitProperties.Policy policy = new RateLimitProperties.Policy(2, Duration.ofMinutes(1));

    RateLimitDecision first = limiter.consume(attempt("first@example.com", policy));
    RateLimitDecision second = limiter.consume(attempt("first@example.com", policy));
    RateLimitDecision denied = limiter.consume(attempt("first@example.com", policy));
    RateLimitDecision separate = limiter.consume(attempt("second@example.com", policy));

    assertThat(first.allowed()).isTrue();
    assertThat(second.allowed()).isTrue();
    assertThat(denied.allowed()).isFalse();
    assertThat(separate.allowed()).isTrue();

    Set<String> keys = redisTemplate.keys("offertrack:rate-limit:v1:login-email:email:*");
    assertThat(keys).hasSize(2);
    assertThat(keys)
        .allSatisfy(
            key -> {
              assertThat(key)
                  .doesNotContain("first@example.com")
                  .doesNotContain("second@example.com");
              assertThat(redisTemplate.getExpire(key)).isPositive().isLessThanOrEqualTo(60);
            });

    clock.advance(Duration.ofMinutes(1));
    assertThat(limiter.consume(attempt("first@example.com", policy)).allowed()).isTrue();
  }

  private static RateLimitAttempt attempt(String email, RateLimitProperties.Policy configuration) {
    return new RateLimitAttempt(
        RateLimitPolicy.LOGIN_EMAIL, RateLimitSubjectType.EMAIL, email, configuration);
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
