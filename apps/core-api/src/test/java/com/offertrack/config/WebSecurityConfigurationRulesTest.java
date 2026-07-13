package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class WebSecurityConfigurationRulesTest {
  @Test
  void validatesWebSecurityIndependently() {
    ProtectedConfigurationSnapshot configuration =
        ProtectedConfigurationSnapshot.from(ProtectedConfigurationRulesTest.validEnvironment());

    assertThatCode(() -> WebSecurityConfigurationRules.validate(configuration))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsCorsOriginWithPath() {
    MockEnvironment environment = ProtectedConfigurationRulesTest.validEnvironment();
    environment.withProperty("app.cors.allowed-origins", "https://app.example.com/path");

    assertThatThrownBy(
            () ->
                WebSecurityConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("app.cors.allowed-origins");
  }
}
