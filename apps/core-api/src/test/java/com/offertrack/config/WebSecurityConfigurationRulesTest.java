package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  @Test
  void requiresGoogleIdentityModeInProtectedConfiguration() {
    MockEnvironment environment = ProtectedConfigurationRulesTest.validEnvironment();
    environment.withProperty("app.ai-service.auth-mode", "internal-key");

    assertThatThrownBy(
            () ->
                WebSecurityConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("app.ai-service.auth-mode");
  }

  @Test
  void acceptsNormalizedEquivalentGoogleAudienceAndBaseUrl() {
    MockEnvironment environment = ProtectedConfigurationRulesTest.validEnvironment();
    environment.withProperty("app.ai-service.base-url", "HTTPS://AI-SERVICE.EXAMPLE.COM:443/");
    environment.withProperty("app.ai-service.audience", "https://ai-service.example.com");

    assertThatCode(
            () ->
                WebSecurityConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsNormalizedGoogleAudienceMismatch() {
    MockEnvironment environment = ProtectedConfigurationRulesTest.validEnvironment();
    environment.withProperty("app.ai-service.audience", "https://other.example.com");

    assertThatThrownBy(
            () ->
                WebSecurityConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("app.ai-service.audience");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://localhost",
        "https://service.local",
        "https://service.localdomain",
        "https://ai-service",
        "https://0.0.0.0"
      })
  void rejectsProtectedLocalAndSingleLabelAiHosts(String endpoint) {
    MockEnvironment environment = ProtectedConfigurationRulesTest.validEnvironment();
    environment.withProperty("app.ai-service.base-url", endpoint);
    environment.withProperty("app.ai-service.audience", endpoint);

    assertThatThrownBy(
            () ->
                WebSecurityConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("app.ai-service");
  }
}
