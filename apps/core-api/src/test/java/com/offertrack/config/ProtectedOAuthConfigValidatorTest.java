package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProtectedOAuthConfigValidatorTest {
  private static final String GOOGLE_CLIENT_ID_PROPERTY =
      "spring.security.oauth2.client.registration.google.client-id";
  private static final String GOOGLE_CLIENT_SECRET_PROPERTY =
      "spring.security.oauth2.client.registration.google.client-secret";
  private static final String JWT_SECRET_PROPERTY = "app.jwt.secret";
  private static final String OAUTH_COOKIE_SECRET_PROPERTY =
      "app.oauth.authorization-request-cookie-signing-secret";
  private static final String AI_SERVICE_INTERNAL_API_KEY_PROPERTY =
      "app.ai-service.internal-api-key";

  @Test
  void defaultProfileAllowsLocalGoogleCredentialsAndSharedCookieSecretFallback() {
    MockEnvironment environment =
        environment()
            .withProperty(GOOGLE_CLIENT_ID_PROPERTY, "local-google-client-id")
            .withProperty(GOOGLE_CLIENT_SECRET_PROPERTY, "local-google-client-secret")
            .withProperty(JWT_SECRET_PROPERTY, "shared-secret")
            .withProperty(OAUTH_COOKIE_SECRET_PROPERTY, "shared-secret")
            .withProperty(AI_SERVICE_INTERNAL_API_KEY_PROPERTY, "local-dev-ai-service-key");

    assertThatCode(() -> new ProtectedOAuthConfigValidator(environment).afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  @Test
  void defaultProfileAllowsMissingAiServiceInternalApiKey() {
    MockEnvironment environment =
        environment()
            .withProperty(GOOGLE_CLIENT_ID_PROPERTY, "local-google-client-id")
            .withProperty(GOOGLE_CLIENT_SECRET_PROPERTY, "local-google-client-secret")
            .withProperty(JWT_SECRET_PROPERTY, "shared-secret")
            .withProperty(OAUTH_COOKIE_SECRET_PROPERTY, "shared-secret");

    assertThatCode(() -> new ProtectedOAuthConfigValidator(environment).afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  @Test
  void protectedProfileRejectsDummyGoogleCredentialsWithoutLeakingValues() {
    MockEnvironment environment =
        protectedEnvironment()
            .withProperty(GOOGLE_CLIENT_ID_PROPERTY, "local-google-client-id")
            .withProperty(GOOGLE_CLIENT_SECRET_PROPERTY, "local-google-client-secret")
            .withProperty(JWT_SECRET_PROPERTY, "jwt-secret")
            .withProperty(OAUTH_COOKIE_SECRET_PROPERTY, "oauth-secret")
            .withProperty(AI_SERVICE_INTERNAL_API_KEY_PROPERTY, "real-ai-service-key");

    assertThatThrownBy(() -> new ProtectedOAuthConfigValidator(environment).afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Protected profiles require real Google OAuth client credentials")
        .hasMessageNotContaining("local-google-client-id")
        .hasMessageNotContaining("local-google-client-secret");
  }

  @Test
  void protectedProfileRejectsSharedOAuthCookieAndJwtSecretsWithoutLeakingValue() {
    MockEnvironment environment =
        protectedEnvironment()
            .withProperty(GOOGLE_CLIENT_ID_PROPERTY, "real-google-client-id")
            .withProperty(GOOGLE_CLIENT_SECRET_PROPERTY, "real-google-client-secret")
            .withProperty(JWT_SECRET_PROPERTY, "shared-secret")
            .withProperty(OAUTH_COOKIE_SECRET_PROPERTY, "shared-secret")
            .withProperty(AI_SERVICE_INTERNAL_API_KEY_PROPERTY, "real-ai-service-key");

    assertThatThrownBy(() -> new ProtectedOAuthConfigValidator(environment).afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Protected profiles require a separate OAuth cookie signing secret")
        .hasMessageNotContaining("shared-secret");
  }

  @Test
  void protectedProfileRejectsMissingAiServiceInternalApiKeyWithoutLeakingValue() {
    MockEnvironment environment =
        protectedEnvironment()
            .withProperty(GOOGLE_CLIENT_ID_PROPERTY, "real-google-client-id")
            .withProperty(GOOGLE_CLIENT_SECRET_PROPERTY, "real-google-client-secret")
            .withProperty(JWT_SECRET_PROPERTY, "jwt-secret")
            .withProperty(OAUTH_COOKIE_SECRET_PROPERTY, "oauth-secret");

    assertThatThrownBy(() -> new ProtectedOAuthConfigValidator(environment).afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Protected profiles require a non-local AI service internal API key");
  }

  @Test
  void protectedProfileRejectsLocalAiServiceInternalApiKeyWithoutLeakingValue() {
    MockEnvironment environment =
        protectedEnvironment()
            .withProperty(GOOGLE_CLIENT_ID_PROPERTY, "real-google-client-id")
            .withProperty(GOOGLE_CLIENT_SECRET_PROPERTY, "real-google-client-secret")
            .withProperty(JWT_SECRET_PROPERTY, "jwt-secret")
            .withProperty(OAUTH_COOKIE_SECRET_PROPERTY, "oauth-secret")
            .withProperty(AI_SERVICE_INTERNAL_API_KEY_PROPERTY, "local-dev-ai-service-key");

    assertThatThrownBy(() -> new ProtectedOAuthConfigValidator(environment).afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Protected profiles require a non-local AI service internal API key")
        .hasMessageNotContaining("local-dev-ai-service-key");
  }

  @Test
  void protectedProfileAllowsRealGoogleCredentialsSeparateOAuthCookieSecretAndAiServiceKey() {
    MockEnvironment environment =
        protectedEnvironment()
            .withProperty(GOOGLE_CLIENT_ID_PROPERTY, "real-google-client-id")
            .withProperty(GOOGLE_CLIENT_SECRET_PROPERTY, "real-google-client-secret")
            .withProperty(JWT_SECRET_PROPERTY, "jwt-secret")
            .withProperty(OAUTH_COOKIE_SECRET_PROPERTY, "oauth-secret")
            .withProperty(AI_SERVICE_INTERNAL_API_KEY_PROPERTY, "real-ai-service-key");

    assertThatCode(() -> new ProtectedOAuthConfigValidator(environment).afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  private static MockEnvironment protectedEnvironment() {
    MockEnvironment environment = environment();
    environment.setActiveProfiles("staging");
    return environment;
  }

  private static MockEnvironment environment() {
    return new MockEnvironment();
  }
}
