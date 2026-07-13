package com.offertrack.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.util.StringUtils;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RateLimitGuardTest {
  @Mock private RateLimiter rateLimiter;

  private RateLimitProperties properties;
  private RateLimitGuard guard;

  @BeforeEach
  void setUp() {
    properties = new RateLimitProperties();
    guard =
        new RateLimitGuard(
            rateLimiter,
            properties,
            Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void consumesEveryApplicablePolicyAndReturnsTheMaximumDeniedRetryAfter() {
    when(rateLimiter.consume(any()))
        .thenReturn(new RateLimitDecision(false, 15))
        .thenReturn(new RateLimitDecision(false, 45));

    assertThatThrownBy(() -> guard.checkLogin(" User@Example.COM ", "203.0.113.10"))
        .isInstanceOfSatisfying(
            RateLimitExceededException.class,
            exception -> {
              assertThat(exception.retryAfterSeconds()).isEqualTo(45);
              assertThat(exception.deniedPolicies())
                  .containsExactly(RateLimitPolicy.LOGIN_EMAIL, RateLimitPolicy.LOGIN_IP);
              assertThat(exception.subjectTypes())
                  .containsExactly(RateLimitSubjectType.EMAIL, RateLimitSubjectType.IP);
            });
    verify(rateLimiter, times(2)).consume(any());
  }

  @Test
  void exposesExactlyTheTwelveConfiguredPoliciesAcrossProtectedOperations() {
    when(rateLimiter.consume(any())).thenReturn(new RateLimitDecision(true, 1));

    guard.checkLogin("user@example.com", "203.0.113.10");
    guard.checkRegistration("user@example.com", "203.0.113.10");
    guard.checkVerificationResend("user@example.com", "203.0.113.10");
    guard.checkForgotPassword("user@example.com", "203.0.113.10");
    guard.checkPasswordReset("reset-token", "203.0.113.10");
    guard.checkAiDraft(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    ArgumentCaptor<RateLimitAttempt> attempts = ArgumentCaptor.forClass(RateLimitAttempt.class);
    verify(rateLimiter, times(12)).consume(attempts.capture());
    assertThat(attempts.getAllValues())
        .extracting(RateLimitAttempt::policy)
        .containsExactly(
            RateLimitPolicy.LOGIN_EMAIL,
            RateLimitPolicy.LOGIN_IP,
            RateLimitPolicy.REGISTRATION_EMAIL,
            RateLimitPolicy.REGISTRATION_IP,
            RateLimitPolicy.VERIFICATION_RESEND_EMAIL,
            RateLimitPolicy.VERIFICATION_RESEND_IP,
            RateLimitPolicy.FORGOT_PASSWORD_EMAIL,
            RateLimitPolicy.FORGOT_PASSWORD_IP,
            RateLimitPolicy.RESET_PASSWORD_TOKEN,
            RateLimitPolicy.RESET_PASSWORD_IP,
            RateLimitPolicy.AI_USER_MINUTE,
            RateLimitPolicy.AI_USER_DAY);
  }

  @Test
  void failClosedStillConsumesRemainingPoliciesBeforeReturningUnavailable() {
    properties.setFailOpen(false);
    when(rateLimiter.consume(any()))
        .thenThrow(new RateLimitStoreUnavailableException())
        .thenReturn(new RateLimitDecision(true, 30));

    assertThatThrownBy(() -> guard.checkLogin("user@example.com", "203.0.113.10"))
        .isInstanceOf(RateLimitServiceUnavailableException.class);
    verify(rateLimiter, times(2)).consume(any());
  }

  @Test
  void failOpenIgnoresUnavailableChecksButHonorsKnownDenials() {
    properties.setFailOpen(true);
    when(rateLimiter.consume(any()))
        .thenThrow(new RateLimitStoreUnavailableException())
        .thenReturn(new RateLimitDecision(false, 30));

    assertThatThrownBy(() -> guard.checkLogin("user@example.com", "203.0.113.10"))
        .isInstanceOf(RateLimitExceededException.class);
    verify(rateLimiter, times(2)).consume(any());
  }

  @Test
  void denialIsReportedWithoutLoggingSubjectsOrPolicyFragments(CapturedOutput output) {
    when(rateLimiter.consume(any()))
        .thenReturn(new RateLimitDecision(false, 30))
        .thenReturn(new RateLimitDecision(true, 30));

    assertThatThrownBy(() -> guard.checkLogin("raw-email-marker@example.com", "raw-ip-marker"))
        .isInstanceOfSatisfying(
            RateLimitExceededException.class,
            exception -> {
              assertThat(exception.deniedPolicies()).containsExactly(RateLimitPolicy.LOGIN_EMAIL);
              assertThat(exception.subjectTypes()).containsExactly(RateLimitSubjectType.EMAIL);
            });

    assertThat(output.getOut())
        .doesNotContain("rate_limit_exceeded", "raw-email-marker", "raw-ip-marker");
  }

  @Test
  void unavailableWarningsAreSuppressedAndNeverIncludeRedisMessages(CapturedOutput output) {
    properties.setFailOpen(true);
    when(rateLimiter.consume(any())).thenThrow(new RateLimitStoreUnavailableException());

    guard.checkLogin("raw-email-marker@example.com", "raw-ip-marker");
    guard.checkLogin("raw-email-marker@example.com", "raw-ip-marker");

    String logs = output.getOut();
    org.assertj.core.api.Assertions.assertThat(
            StringUtils.countOccurrencesOf(logs, "rate_limiter_unavailable"))
        .isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(logs)
        .doesNotContain("raw-email-marker", "raw-ip-marker");
  }

  @Test
  void unavailableWarningIsEmittedAgainAfterTheSuppressionInterval(CapturedOutput output) {
    MutableClock mutableClock = new MutableClock(Instant.parse("2026-07-12T12:00:00Z"));
    properties.setFailOpen(true);
    guard = new RateLimitGuard(rateLimiter, properties, mutableClock);
    when(rateLimiter.consume(any())).thenThrow(new RateLimitStoreUnavailableException());

    guard.checkLogin("user@example.com", "203.0.113.10");
    mutableClock.advance(Duration.ofMinutes(1));
    guard.checkLogin("user@example.com", "203.0.113.10");

    assertThat(StringUtils.countOccurrencesOf(output.getOut(), "rate_limiter_unavailable"))
        .isEqualTo(2);
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
