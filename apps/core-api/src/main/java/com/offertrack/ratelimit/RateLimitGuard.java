package com.offertrack.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class RateLimitGuard {
  private static final Logger log = LoggerFactory.getLogger(RateLimitGuard.class);
  private static final Duration UNAVAILABLE_LOG_INTERVAL = Duration.ofMinutes(1);

  private final RedisRateLimiter rateLimiter;
  private final RateLimitProperties properties;
  private final Clock clock;
  private final AtomicLong nextUnavailableWarningAt = new AtomicLong(Long.MIN_VALUE);

  RateLimitGuard(RedisRateLimiter rateLimiter, RateLimitProperties properties, Clock clock) {
    this.rateLimiter = rateLimiter;
    this.properties = properties;
    this.clock = clock;
  }

  public void checkLogin(String email, String remoteAddress) {
    checkAll(
        List.of(
            attempt(
                RateLimitPolicy.LOGIN_EMAIL,
                RateLimitSubjectType.EMAIL,
                normalizeEmail(email),
                properties.getLoginEmail()),
            attempt(
                RateLimitPolicy.LOGIN_IP,
                RateLimitSubjectType.IP,
                normalizeRemoteAddress(remoteAddress),
                properties.getLoginIp())));
  }

  public void checkRegistration(String email, String remoteAddress) {
    checkAll(
        List.of(
            attempt(
                RateLimitPolicy.REGISTRATION_EMAIL,
                RateLimitSubjectType.EMAIL,
                normalizeEmail(email),
                properties.getRegistrationEmail()),
            attempt(
                RateLimitPolicy.REGISTRATION_IP,
                RateLimitSubjectType.IP,
                normalizeRemoteAddress(remoteAddress),
                properties.getRegistrationIp())));
  }

  public void checkVerificationResend(String email, String remoteAddress) {
    checkAll(
        List.of(
            attempt(
                RateLimitPolicy.VERIFICATION_RESEND_EMAIL,
                RateLimitSubjectType.EMAIL,
                normalizeEmail(email),
                properties.getVerificationResendEmail()),
            attempt(
                RateLimitPolicy.VERIFICATION_RESEND_IP,
                RateLimitSubjectType.IP,
                normalizeRemoteAddress(remoteAddress),
                properties.getVerificationResendIp())));
  }

  public void checkForgotPassword(String email, String remoteAddress) {
    checkAll(
        List.of(
            attempt(
                RateLimitPolicy.FORGOT_PASSWORD_EMAIL,
                RateLimitSubjectType.EMAIL,
                normalizeEmail(email),
                properties.getForgotPasswordEmail()),
            attempt(
                RateLimitPolicy.FORGOT_PASSWORD_IP,
                RateLimitSubjectType.IP,
                normalizeRemoteAddress(remoteAddress),
                properties.getForgotPasswordIp())));
  }

  public void checkPasswordReset(String token, String remoteAddress) {
    checkAll(
        List.of(
            attempt(
                RateLimitPolicy.RESET_PASSWORD_TOKEN,
                RateLimitSubjectType.TOKEN,
                token,
                properties.getResetPasswordToken()),
            attempt(
                RateLimitPolicy.RESET_PASSWORD_IP,
                RateLimitSubjectType.IP,
                normalizeRemoteAddress(remoteAddress),
                properties.getResetPasswordIp())));
  }

  public void checkAiDraft(UUID userId) {
    checkAll(
        List.of(
            attempt(
                RateLimitPolicy.AI_USER_MINUTE,
                RateLimitSubjectType.USER,
                userId.toString(),
                properties.getAiUserMinute()),
            attempt(
                RateLimitPolicy.AI_USER_DAY,
                RateLimitSubjectType.USER,
                userId.toString(),
                properties.getAiUserDay())));
  }

  private void checkAll(List<RateLimitAttempt> attempts) {
    List<DeniedAttempt> deniedAttempts = new ArrayList<>();
    boolean unavailable = false;

    for (RateLimitAttempt attempt : attempts) {
      try {
        RateLimitDecision decision = rateLimiter.consume(attempt);
        if (!decision.allowed()) {
          deniedAttempts.add(new DeniedAttempt(attempt, decision.retryAfterSeconds()));
        }
      } catch (DataAccessException exception) {
        unavailable = true;
        logUnavailableIfDue();
      }
    }

    if (unavailable && !properties.isFailOpen()) {
      throw new RateLimitServiceUnavailableException();
    }

    if (deniedAttempts.isEmpty()) {
      return;
    }

    for (DeniedAttempt denied : deniedAttempts) {
      log.warn(
          "rate_limit_exceeded policy={} subject_type={} retry_after={}",
          denied.attempt().policy().key(),
          denied.attempt().subjectType().key(),
          denied.retryAfterSeconds());
    }

    long retryAfterSeconds =
        deniedAttempts.stream().mapToLong(DeniedAttempt::retryAfterSeconds).max().orElse(1);
    throw new RateLimitExceededException(retryAfterSeconds);
  }

  private void logUnavailableIfDue() {
    long now = clock.millis();
    long current = nextUnavailableWarningAt.get();
    if (now < current) {
      return;
    }

    long next = now + UNAVAILABLE_LOG_INTERVAL.toMillis();
    if (nextUnavailableWarningAt.compareAndSet(current, next)) {
      log.warn("rate_limiter_unavailable fail_open={}", properties.isFailOpen());
    }
  }

  private static RateLimitAttempt attempt(
      RateLimitPolicy policy,
      RateLimitSubjectType subjectType,
      String subject,
      RateLimitProperties.Policy configuration) {
    return new RateLimitAttempt(policy, subjectType, subject, configuration);
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static String normalizeRemoteAddress(String remoteAddress) {
    return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
  }

  private record DeniedAttempt(RateLimitAttempt attempt, long retryAfterSeconds) {}
}
