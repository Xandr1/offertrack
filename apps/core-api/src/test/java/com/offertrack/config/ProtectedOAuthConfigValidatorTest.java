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

  @Test
  void defaultProfileAllowsLocalGoogleCredentialsAndSharedCookieSecretFallback() {
    MockEnvironment environment =
        environment()
            .withProperty(GOOGLE_CLIENT_ID_PROPERTY, "local-google-client-id")
            .withProperty(GOOGLE_CLIENT_SECRET_PROPERTY, "local-google-client-secret")
            .withProperty("app.jwt.secret", "shared-secret")
            .withProperty("app.oauth.authorization-request-cookie-signing-secret", "shared-secret");

    assertThatCode(() -> new ProtectedOAuthConfigValidator(environment).afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  @Test
  void protectedProfileRejectsDummyGoogleCredentialsWithoutLeakingValues() {
    MockEnvironment environment =
        protectedEnvironment()
            .withProperty(GOOGLE_CLIENT_ID_PROPERTY, "local-google-client-id")
            .withProperty(GOOGLE_CLIENT_SECRET_PROPERTY, "local-google-client-secret")
            .withProperty("app.jwt.secret", "jwt-secret")
            .withProperty("app.oauth.authorization-request-cookie-signing-secret", "oauth-secret");

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
            .withProperty("app.jwt.secret", "shared-secret")
            .withProperty("app.oauth.authorization-request-cookie-signing-secret", "shared-secret");

    assertThatThrownBy(() -> new ProtectedOAuthConfigValidator(environment).afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Protected profiles require a separate OAuth cookie signing secret")
        .hasMessageNotContaining("shared-secret");
  }

  @Test
  void protectedProfileAllowsRealGoogleCredentialsAndSeparateOAuthCookieSecret() {
    MockEnvironment environment =
        protectedEnvironment()
            .withProperty(GOOGLE_CLIENT_ID_PROPERTY, "real-google-client-id")
            .withProperty(GOOGLE_CLIENT_SECRET_PROPERTY, "real-google-client-secret")
            .withProperty("app.jwt.secret", "jwt-secret")
            .withProperty("app.oauth.authorization-request-cookie-signing-secret", "oauth-secret");

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
