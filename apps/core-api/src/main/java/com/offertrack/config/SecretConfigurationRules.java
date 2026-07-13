package com.offertrack.config;

import static com.offertrack.config.ConfigurationRuleSupport.invalid;
import static com.offertrack.config.ConfigurationRuleSupport.requirePositiveDuration;
import static com.offertrack.config.ConfigurationRuleSupport.requireSecret;

final class SecretConfigurationRules {
  private static final int MINIMUM_SECRET_BYTES = 32;
  private static final String LOCAL_JWT_SECRET =
      "change-me-change-me-change-me-change-me-change-me";
  private static final String LOCAL_AI_KEY = "local-dev-ai-service-key";
  private static final String LOCAL_RATE_LIMIT_KEY = "local-dev-rate-limit-key-secret-change-me";

  private SecretConfigurationRules() {}

  static void validate(ProtectedConfigurationSnapshot configuration) {
    requireSecret(
        configuration.jwtSecret(), "app.jwt.secret", MINIMUM_SECRET_BYTES, LOCAL_JWT_SECRET);
    requireSecret(
        configuration.oauthCookieSecret(),
        "app.oauth.authorization-request-cookie-signing-secret",
        MINIMUM_SECRET_BYTES);
    requireSecret(
        configuration.rateLimitKeySecret(),
        "app.rate-limit.key-secret",
        MINIMUM_SECRET_BYTES,
        LOCAL_RATE_LIMIT_KEY);
    requireSecret(
        configuration.aiServiceInternalApiKey(),
        "app.ai-service.internal-api-key",
        MINIMUM_SECRET_BYTES,
        LOCAL_AI_KEY);
    requirePositiveDuration(configuration.jwtAccessTokenTtl(), "app.jwt.access-token-ttl");

    if (configuration.jwtSecret().trim().equals(configuration.oauthCookieSecret().trim())) {
      invalid("app.oauth.authorization-request-cookie-signing-secret");
    }
    if (configuration.rateLimitKeySecret().trim().equals(configuration.jwtSecret().trim())
        || configuration
            .rateLimitKeySecret()
            .trim()
            .equals(configuration.oauthCookieSecret().trim())) {
      invalid("app.rate-limit.key-secret");
    }
  }
}
