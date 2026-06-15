package com.offertrack.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProtectedOAuthConfigValidator implements InitializingBean {
  private static final Set<String> PROTECTED_PROFILES =
      Set.of("prod", "production", "staging", "stage");
  private static final String LOCAL_GOOGLE_CLIENT_ID = "local-google-client-id";
  private static final String LOCAL_GOOGLE_CLIENT_SECRET = "local-google-client-secret";

  private final Environment environment;

  public ProtectedOAuthConfigValidator(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void afterPropertiesSet() {
    if (!hasProtectedProfile()) {
      return;
    }

    validateGoogleCredentials();
    validateOAuthCookieSecret();
  }

  private boolean hasProtectedProfile() {
    // Deployment configs must explicitly activate a protected profile for these checks to run.
    return Arrays.stream(environment.getActiveProfiles())
        .map(profile -> profile.toLowerCase(Locale.ROOT))
        .anyMatch(PROTECTED_PROFILES::contains);
  }

  private void validateGoogleCredentials() {
    String clientId =
        environment.getProperty("spring.security.oauth2.client.registration.google.client-id");
    String clientSecret =
        environment.getProperty("spring.security.oauth2.client.registration.google.client-secret");

    if (!StringUtils.hasText(clientId)
        || !StringUtils.hasText(clientSecret)
        || LOCAL_GOOGLE_CLIENT_ID.equals(clientId)
        || LOCAL_GOOGLE_CLIENT_SECRET.equals(clientSecret)) {
      throw new IllegalStateException(
          "Protected profiles require real Google OAuth client credentials");
    }
  }

  private void validateOAuthCookieSecret() {
    String oauthCookieSecret =
        environment.getProperty("app.oauth.authorization-request-cookie-signing-secret");
    String jwtSecret = environment.getProperty("app.jwt.secret");

    if (!StringUtils.hasText(oauthCookieSecret)
        || !StringUtils.hasText(jwtSecret)
        || oauthCookieSecret.equals(jwtSecret)) {
      throw new IllegalStateException(
          "Protected profiles require a separate OAuth cookie signing secret");
    }
  }
}
