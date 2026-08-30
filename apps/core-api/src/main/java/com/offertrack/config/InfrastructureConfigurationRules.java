package com.offertrack.config;

import static com.offertrack.config.ConfigurationRuleSupport.invalid;
import static com.offertrack.config.ConfigurationRuleSupport.requireBoolean;
import static com.offertrack.config.ConfigurationRuleSupport.requireCredential;
import static com.offertrack.config.ConfigurationRuleSupport.requirePort;
import static com.offertrack.config.ConfigurationRuleSupport.requirePositiveDuration;
import static com.offertrack.config.ConfigurationRuleSupport.requireText;

import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

final class InfrastructureConfigurationRules {
  private static final String REDIS_SSL_BUNDLE = "offertrack-redis";
  private static final String LOCAL_OAUTH_CLIENT_ID = "local-google-client-id";
  private static final String LOCAL_OAUTH_CLIENT_SECRET = "local-google-client-secret";
  private static final Set<String> FORWARD_HEADER_STRATEGIES =
      Set.of("none", "framework", "native");

  private InfrastructureConfigurationRules() {}

  static void validate(ProtectedConfigurationSnapshot configuration) {
    validateDatabase(configuration);
    requirePort(configuration.serverPort(), "server.port");
    validateRedis(configuration);
    validateMail(configuration);
    validateGoogle(configuration);
  }

  static void validateDatabase(ProtectedConfigurationSnapshot configuration) {
    try {
      DatabaseConfigurationValidator.validate(
          new DatabaseConfiguration(
              configuration.databaseUrl(),
              configuration.databaseUsername(),
              configuration.databasePassword()),
          DatabaseConfigurationValidator.Policy.PROTECTED,
          DatabaseConfigurationValidator.PropertyNames.springDatasource());
    } catch (DatabaseConfigurationValidationException exception) {
      throw ConfigurationRuleSupport.invalidException(exception.property());
    }
  }

  private static void validateRedis(ProtectedConfigurationSnapshot configuration) {
    HostValidation.requireExplicitNonLoopbackHost(
        configuration.redisHost(), "spring.data.redis.host");
    requirePort(configuration.redisPort(), "spring.data.redis.port");
    requirePositiveDuration(
        configuration.redisConnectTimeout(), "spring.data.redis.connect-timeout");
    requirePositiveDuration(configuration.redisTimeout(), "spring.data.redis.timeout");
    validateRedisTls(configuration);
  }

  private static void validateRedisTls(ProtectedConfigurationSnapshot configuration) {
    String enabledProperty = "spring.data.redis.ssl.enabled";
    requireBoolean(configuration.redisTlsEnabled(), enabledProperty);
    if (!Boolean.parseBoolean(configuration.redisTlsEnabled().trim())) {
      invalid(enabledProperty);
    }

    String bundleProperty = "spring.data.redis.ssl.bundle";
    requireText(configuration.redisTlsBundle(), bundleProperty);
    if (!REDIS_SSL_BUNDLE.equals(configuration.redisTlsBundle().trim())) {
      invalid(bundleProperty);
    }

    RedisTlsTrustMaterialValidator.validate(configuration.redisTlsCaCertificates());
  }

  private static void validateMail(ProtectedConfigurationSnapshot configuration) {
    HostValidation.requireExplicitNonLoopbackHost(configuration.smtpHost(), "spring.mail.host");
    requirePort(configuration.smtpPort(), "spring.mail.port");
    boolean hasUsername = StringUtils.hasText(configuration.smtpUsername());
    boolean hasPassword = StringUtils.hasText(configuration.smtpPassword());
    if (hasUsername != hasPassword) {
      invalid(hasUsername ? "spring.mail.password" : "spring.mail.username");
    }
    if (hasUsername) {
      requireCredential(
          configuration.smtpUsername(), "spring.mail.username", 1, "username", "changeme");
      requireCredential(
          configuration.smtpPassword(), "spring.mail.password", 12, "password", "changeme");
    }
    requireText(configuration.mailFrom(), "app.mail.from");
    if (!configuration.mailFrom().contains("@")) {
      invalid("app.mail.from");
    }
  }

  private static void validateGoogle(ProtectedConfigurationSnapshot configuration) {
    String idProperty = "spring.security.oauth2.client.registration.google.client-id";
    String secretProperty = "spring.security.oauth2.client.registration.google.client-secret";
    requireText(configuration.googleClientId(), idProperty);
    requireText(configuration.googleClientSecret(), secretProperty);
    if (LOCAL_OAUTH_CLIENT_ID.equals(configuration.googleClientId().trim())) {
      invalid(idProperty);
    }
    if (LOCAL_OAUTH_CLIENT_SECRET.equals(configuration.googleClientSecret().trim())) {
      invalid(secretProperty);
    }
    requireCredential(
        configuration.googleClientSecret(), secretProperty, 16, LOCAL_OAUTH_CLIENT_SECRET);
  }

  static void validateForwardHeaders(String value) {
    requireText(value, "server.forward-headers-strategy");
    if (!FORWARD_HEADER_STRATEGIES.contains(value.trim().toLowerCase(Locale.ROOT))) {
      invalid("server.forward-headers-strategy");
    }
  }
}
