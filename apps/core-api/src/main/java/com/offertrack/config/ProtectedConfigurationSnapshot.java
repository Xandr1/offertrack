package com.offertrack.config;

import java.util.List;
import org.springframework.core.env.Environment;

public record ProtectedConfigurationSnapshot(
    String databaseUrl,
    String databaseUsername,
    String databasePassword,
    String serverPort,
    String redisHost,
    String redisPort,
    String redisConnectTimeout,
    String redisTimeout,
    String smtpHost,
    String smtpPort,
    String smtpUsername,
    String smtpPassword,
    String mailFrom,
    String googleClientId,
    String googleClientSecret,
    String jwtSecret,
    String jwtAccessTokenTtl,
    String oauthCookieSecret,
    String rateLimitKeySecret,
    String rateLimitFailOpen,
    String webUrl,
    String corsAllowedOrigins,
    String accessCookieName,
    String accessCookiePath,
    String accessCookieDomain,
    String accessCookieSecure,
    String accessCookieSameSite,
    String aiServiceBaseUrl,
    String aiServiceInternalApiKey,
    String aiServiceAuthMode,
    String aiServiceAudience,
    String forwardHeadersStrategy,
    List<RateLimitPolicyValue> rateLimitPolicies) {
  private static final List<String> RATE_LIMIT_POLICY_NAMES =
      List.of(
          "login-email",
          "login-ip",
          "registration-email",
          "registration-ip",
          "verification-resend-email",
          "verification-resend-ip",
          "forgot-password-email",
          "forgot-password-ip",
          "reset-password-token",
          "reset-password-ip",
          "ai-user-minute",
          "ai-user-day");

  public static ProtectedConfigurationSnapshot from(Environment environment) {
    return new ProtectedConfigurationSnapshot(
        environment.getProperty("spring.datasource.url"),
        environment.getProperty("spring.datasource.username"),
        environment.getProperty("spring.datasource.password"),
        environment.getProperty("server.port"),
        environment.getProperty("spring.data.redis.host"),
        environment.getProperty("spring.data.redis.port"),
        environment.getProperty("spring.data.redis.connect-timeout"),
        environment.getProperty("spring.data.redis.timeout"),
        environment.getProperty("spring.mail.host"),
        environment.getProperty("spring.mail.port"),
        environment.getProperty("spring.mail.username"),
        environment.getProperty("spring.mail.password"),
        environment.getProperty("app.mail.from"),
        environment.getProperty("spring.security.oauth2.client.registration.google.client-id"),
        environment.getProperty("spring.security.oauth2.client.registration.google.client-secret"),
        environment.getProperty("app.jwt.secret"),
        environment.getProperty("app.jwt.access-token-ttl"),
        environment.getProperty("app.oauth.authorization-request-cookie-signing-secret"),
        environment.getProperty("app.rate-limit.key-secret"),
        environment.getProperty("app.rate-limit.fail-open"),
        environment.getProperty("app.web.url"),
        environment.getProperty("app.cors.allowed-origins"),
        environment.getProperty("app.auth.cookie.name"),
        environment.getProperty("app.auth.cookie.path"),
        environment.getProperty("app.auth.cookie.domain"),
        environment.getProperty("app.auth.cookie.secure"),
        environment.getProperty("app.auth.cookie.same-site"),
        environment.getProperty("app.ai-service.base-url"),
        environment.getProperty("app.ai-service.internal-api-key"),
        environment.getProperty("app.ai-service.auth-mode"),
        environment.getProperty("app.ai-service.audience"),
        environment.getProperty("server.forward-headers-strategy"),
        RATE_LIMIT_POLICY_NAMES.stream()
            .map(
                name -> {
                  String prefix = "app.rate-limit." + name;
                  return new RateLimitPolicyValue(
                      prefix,
                      environment.getProperty(prefix + ".max-attempts"),
                      environment.getProperty(prefix + ".window"));
                })
            .toList());
  }

  public record RateLimitPolicyValue(String propertyPrefix, String maxAttempts, String window) {}
}
