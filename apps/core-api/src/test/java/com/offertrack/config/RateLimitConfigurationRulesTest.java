package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RateLimitConfigurationRulesTest {
  @Test
  void validatesRateLimitsIndependently() {
    ProtectedConfigurationSnapshot configuration =
        ProtectedConfigurationSnapshot.from(ProtectedConfigurationRulesTest.validEnvironment());

    assertThatCode(() -> RateLimitConfigurationRules.validate(configuration))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsSubsecondWindow() {
    MockEnvironment environment = ProtectedConfigurationRulesTest.validEnvironment();
    environment.withProperty("app.rate-limit.login-ip.window", "999ms");

    assertThatThrownBy(
            () ->
                RateLimitConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("app.rate-limit.login-ip.window");
  }
}
