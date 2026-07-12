package com.offertrack.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitPropertiesTest {
  @Test
  void usesTheTwelveOperationalDefaults() {
    RateLimitProperties properties = new RateLimitProperties();

    assertPolicy(properties.getLoginEmail(), 5, Duration.ofMinutes(15));
    assertPolicy(properties.getLoginIp(), 20, Duration.ofMinutes(15));
    assertPolicy(properties.getRegistrationEmail(), 3, Duration.ofHours(1));
    assertPolicy(properties.getRegistrationIp(), 5, Duration.ofHours(1));
    assertPolicy(properties.getVerificationResendEmail(), 3, Duration.ofHours(1));
    assertPolicy(properties.getVerificationResendIp(), 10, Duration.ofHours(1));
    assertPolicy(properties.getForgotPasswordEmail(), 5, Duration.ofHours(1));
    assertPolicy(properties.getForgotPasswordIp(), 20, Duration.ofHours(1));
    assertPolicy(properties.getResetPasswordToken(), 10, Duration.ofHours(1));
    assertPolicy(properties.getResetPasswordIp(), 20, Duration.ofHours(1));
    assertPolicy(properties.getAiUserMinute(), 10, Duration.ofMinutes(1));
    assertPolicy(properties.getAiUserDay(), 100, Duration.ofDays(1));
    assertThat(properties.isFailOpen()).isTrue();
  }

  private static void assertPolicy(
      RateLimitProperties.Policy policy, int maxAttempts, Duration window) {
    assertThat(policy.getMaxAttempts()).isEqualTo(maxAttempts);
    assertThat(policy.getWindow()).isEqualTo(window);
  }
}
