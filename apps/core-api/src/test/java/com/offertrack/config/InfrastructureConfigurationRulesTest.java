package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class InfrastructureConfigurationRulesTest {
  @Test
  void validatesInfrastructureIndependently() {
    ProtectedConfigurationSnapshot configuration =
        configuration(ProtectedConfigurationRulesTest.validEnvironment());

    assertThatCode(() -> InfrastructureConfigurationRules.validate(configuration))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsMalformedRedisHostWithoutLeakingItsValue() {
    MockEnvironment environment = ProtectedConfigurationRulesTest.validEnvironment();
    environment.withProperty("spring.data.redis.host", "redis host containing secret-marker");

    assertThatThrownBy(() -> InfrastructureConfigurationRules.validate(configuration(environment)))
        .hasMessageContaining("spring.data.redis.host")
        .hasMessageNotContaining("secret-marker");
  }

  private static ProtectedConfigurationSnapshot configuration(MockEnvironment environment) {
    return ProtectedConfigurationSnapshot.from(environment);
  }
}
