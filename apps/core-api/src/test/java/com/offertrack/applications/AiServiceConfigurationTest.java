package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiServiceConfigurationTest {
  @Test
  void defaultsToInternalKeyWithoutAudience() {
    ValidatedAiServiceConfiguration configuration =
        ValidatedAiServiceConfiguration.from(new AiServiceProperties());

    assertThat(configuration.authMode()).isEqualTo(AiServiceAuthMode.INTERNAL_KEY);
    assertThat(configuration.audience()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "INTERNAL-KEY", "google_id_token", "unknown"})
  void rejectsBlankInexactAndUnknownAuthModes(String authMode) {
    AiServiceProperties properties = validGoogleProperties();
    properties.setAuthMode(authMode);

    assertThatThrownBy(() -> ValidatedAiServiceConfiguration.from(properties))
        .isInstanceOf(AiServiceConfigurationException.class)
        .hasMessageContaining("app.ai-service.auth-mode");
  }

  @Test
  void requiresAudienceForGoogleMode() {
    AiServiceProperties properties = validGoogleProperties();
    properties.setAudience("");

    assertThatThrownBy(() -> ValidatedAiServiceConfiguration.from(properties))
        .isInstanceOf(AiServiceConfigurationException.class)
        .hasMessageContaining("app.ai-service.audience");
  }

  @Test
  void acceptsNormalizedEquivalentRootsAndRetainsExactAudience() {
    AiServiceProperties properties = validGoogleProperties();
    properties.setBaseUrl("HTTPS://AI-SERVICE.EXAMPLE.COM:443/");
    properties.setAudience("https://ai-service.example.com");

    ValidatedAiServiceConfiguration configuration =
        ValidatedAiServiceConfiguration.from(properties);

    assertThat(configuration.audience()).isEqualTo("https://ai-service.example.com");
    assertThat(configuration.baseUrl()).isEqualTo("HTTPS://AI-SERVICE.EXAMPLE.COM:443/");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://ai-service.example.com",
        "https://user:secret@ai-service.example.com",
        "https://ai-service.example.com/path",
        "https://ai-service.example.com?tenant=one",
        "https://ai-service.example.com#fragment",
        " https://ai-service.example.com",
        "https://ai-service.example.com:",
        "https://ai-service.example.com:0",
        "https://ai-service.example.com:65536"
      })
  void rejectsInvalidGoogleAudienceRoots(String audience) {
    AiServiceProperties properties = validGoogleProperties();
    properties.setAudience(audience);

    assertThatThrownBy(() -> ValidatedAiServiceConfiguration.from(properties))
        .isInstanceOf(AiServiceConfigurationException.class)
        .hasMessageContaining("app.ai-service.audience");
  }

  @Test
  void preservesMatchingNonDefaultPortsDuringNormalization() {
    AiServiceProperties properties = validGoogleProperties();
    properties.setBaseUrl("HTTPS://AI-SERVICE.EXAMPLE.COM:8443/");
    properties.setAudience("https://ai-service.example.com:8443");

    ValidatedAiServiceConfiguration configuration =
        ValidatedAiServiceConfiguration.from(properties);

    assertThat(configuration.audience()).isEqualTo("https://ai-service.example.com:8443");
  }

  @Test
  void rejectsNormalizedBaseAndAudienceMismatch() {
    AiServiceProperties properties = validGoogleProperties();
    properties.setAudience("https://other-service.example.com");

    assertThatThrownBy(() -> ValidatedAiServiceConfiguration.from(properties))
        .isInstanceOf(AiServiceConfigurationException.class)
        .hasMessageContaining("app.ai-service.audience");
  }

  @Test
  void rejectsWhitespaceAroundGoogleBaseUrl() {
    AiServiceProperties properties = validGoogleProperties();
    properties.setBaseUrl(" https://ai-service.example.com");

    assertThatThrownBy(() -> ValidatedAiServiceConfiguration.from(properties))
        .isInstanceOf(AiServiceConfigurationException.class)
        .hasMessageContaining("app.ai-service.base-url");
  }

  @Test
  void requiresInternalKeyInBothModes() {
    AiServiceProperties internalProperties = new AiServiceProperties();
    internalProperties.setInternalApiKey(" ");
    AiServiceProperties googleProperties = validGoogleProperties();
    googleProperties.setInternalApiKey("");

    assertThatThrownBy(() -> ValidatedAiServiceConfiguration.from(internalProperties))
        .hasMessageContaining("app.ai-service.internal-api-key");
    assertThatThrownBy(() -> ValidatedAiServiceConfiguration.from(googleProperties))
        .hasMessageContaining("app.ai-service.internal-api-key");
  }

  private static AiServiceProperties validGoogleProperties() {
    AiServiceProperties properties = new AiServiceProperties();
    properties.setBaseUrl("https://ai-service.example.com");
    properties.setInternalApiKey("internal-key");
    properties.setAuthMode("google-id-token");
    properties.setAudience("https://ai-service.example.com/");
    return properties;
  }
}
