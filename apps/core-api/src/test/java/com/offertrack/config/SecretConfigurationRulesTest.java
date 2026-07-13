package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SecretConfigurationRulesTest {
  @Test
  void validatesSecretsIndependently() {
    ProtectedConfigurationSnapshot configuration =
        ProtectedConfigurationSnapshot.from(ProtectedConfigurationRulesTest.validEnvironment());

    assertThatCode(() -> SecretConfigurationRules.validate(configuration))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsSharedSecretsWithoutLeakingThem() {
    MockEnvironment environment = ProtectedConfigurationRulesTest.validEnvironment();
    String jwtSecret = environment.getProperty("app.jwt.secret");
    environment.withProperty("app.oauth.authorization-request-cookie-signing-secret", jwtSecret);

    assertThatThrownBy(
            () ->
                SecretConfigurationRules.validate(ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("app.oauth.authorization-request-cookie-signing-secret")
        .hasMessageNotContaining(jwtSecret);
  }
}
