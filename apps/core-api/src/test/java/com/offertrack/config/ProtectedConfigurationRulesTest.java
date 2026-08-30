package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProtectedConfigurationRulesTest {
  @Test
  void acceptsCompleteProtectedConfiguration() {
    assertThatCode(() -> ProtectedConfigurationRules.validate(validConfiguration()))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsTheFullActiveRedisCaSet() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty(
        RedisTlsTrustMaterialValidator.PROPERTY,
        redisCaCertificate() + "\n" + redisOtherCaCertificate());

    assertThatCode(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsDisabledRedisTlsInProtectedProfiles() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("spring.data.redis.ssl.enabled", "false");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("spring.data.redis.ssl.enabled");
  }

  @Test
  void rejectsMissingRedisTrustMaterial() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty(RedisTlsTrustMaterialValidator.PROPERTY, "");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining(RedisTlsTrustMaterialValidator.PROPERTY);
  }

  @Test
  void rejectsInvalidRedisTrustMaterialWithoutLeakingIt() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty(
        RedisTlsTrustMaterialValidator.PROPERTY, "invalid-certificate-secret-marker");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining(RedisTlsTrustMaterialValidator.PROPERTY)
        .hasMessageNotContaining("secret-marker");
  }

  @Test
  void rejectsAValidEndEntityCertificateAsRedisTrustMaterial() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty(
        RedisTlsTrustMaterialValidator.PROPERTY, resource("/redis/tls/server.crt"));

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining(RedisTlsTrustMaterialValidator.PROPERTY);
  }

  @Test
  void rejectsKnownJwtPlaceholderWithoutLeakingIt() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("app.jwt.secret", "change-me-change-me-change-me-change-me-change-me");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.jwt.secret")
        .hasMessageNotContaining("change-me-change-me");
  }

  @Test
  void rejectsShortSecretWithoutClaimingEntropyValidation() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("app.oauth.authorization-request-cookie-signing-secret", "short");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.oauth.authorization-request-cookie-signing-secret")
        .hasMessageNotContaining("short")
        .hasMessageNotContaining("entropy");
  }

  @Test
  void rejectsShortSecretPaddedWithWhitespace() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("app.rate-limit.key-secret", " short " + " ".repeat(40));

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("app.rate-limit.key-secret");
  }

  @Test
  void rejectsRateLimitSecretEqualToJwtSecret() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("app.rate-limit.key-secret", jwtSecret());

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.rate-limit.key-secret")
        .hasMessageNotContaining(jwtSecret());
  }

  @Test
  void rejectsLoopbackInfrastructureAndWebHosts() {
    MockEnvironment databaseEnvironment = validEnvironment();
    databaseEnvironment.withProperty(
        "spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/offertrack");
    MockEnvironment webEnvironment = validEnvironment();
    webEnvironment.withProperty("app.web.url", "https://localhost");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(databaseEnvironment)))
        .hasMessageContaining("spring.datasource.url");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(webEnvironment)))
        .hasMessageContaining("app.web.url");
  }

  @Test
  void rejectsAmbiguousDecimalIntegerLoopbackHosts() {
    MockEnvironment databaseEnvironment = validEnvironment();
    databaseEnvironment.withProperty(
        "spring.datasource.url", "jdbc:postgresql://2130706433:5432/offertrack");
    MockEnvironment webEnvironment = validEnvironment();
    webEnvironment.withProperty("app.web.url", "https://2130706433");
    MockEnvironment zeroPaddedLoopbackEnvironment = validEnvironment();
    zeroPaddedLoopbackEnvironment.withProperty("app.web.url", "https://0127.0.0.1");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(databaseEnvironment)))
        .hasMessageContaining("spring.datasource.url");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(webEnvironment)))
        .hasMessageContaining("app.web.url");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(zeroPaddedLoopbackEnvironment)))
        .hasMessageContaining("app.web.url");
  }

  @Test
  void rejectsIpv4MappedIpv6LoopbackHosts() {
    MockEnvironment dottedMappedAddress = validEnvironment();
    dottedMappedAddress.withProperty("app.ai-service.base-url", "https://[::ffff:127.0.0.1]:8000");
    MockEnvironment expandedMappedAddress = validEnvironment();
    expandedMappedAddress.withProperty(
        "app.ai-service.base-url", "https://[0:0:0:0:0:ffff:7f00:1]:8000");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(dottedMappedAddress)))
        .hasMessageContaining("app.ai-service.base-url");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(expandedMappedAddress)))
        .hasMessageContaining("app.ai-service.base-url");
  }

  @Test
  void rejectsCanonicalLoopbackAliasesAndCompressedIpv6Forms() {
    MockEnvironment rootedLocalhost = validEnvironment();
    rootedLocalhost.withProperty("app.web.url", "https://app.localhost.");
    MockEnvironment compressedLoopback = validEnvironment();
    compressedLoopback.withProperty("app.ai-service.base-url", "https://[0::1]:8000");
    MockEnvironment compressedUnspecified = validEnvironment();
    compressedUnspecified.withProperty("app.ai-service.base-url", "https://[0::]:8000");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(rootedLocalhost)))
        .hasMessageContaining("app.web.url");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(compressedLoopback)))
        .hasMessageContaining("app.ai-service.base-url");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(compressedUnspecified)))
        .hasMessageContaining("app.ai-service.base-url");
  }

  @Test
  void rejectsInexactOrInsecureCorsOrigins() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("app.cors.allowed-origins", "https://app.example.com/path");
    MockEnvironment trailingEmptyOrigin = validEnvironment();
    trailingEmptyOrigin.withProperty(
        "app.cors.allowed-origins", "https://app.example.com,https://admin.example.com,");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("app.cors.allowed-origins");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(trailingEmptyOrigin)))
        .hasMessageContaining("app.cors.allowed-origins");
  }

  @Test
  void rejectsOutOfRangeHttpPort() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("app.web.url", "https://app.example.com:70000");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("app.web.url");
  }

  @Test
  void rejectsUrlAndCorsWhitespaceThatRuntimePropertiesWouldRetain() {
    MockEnvironment webEnvironment = validEnvironment();
    webEnvironment.withProperty("app.web.url", " https://app.example.com ");
    MockEnvironment corsEnvironment = validEnvironment();
    corsEnvironment.withProperty(
        "app.cors.allowed-origins", "https://app.example.com, https://admin.example.com");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(webEnvironment)))
        .hasMessageContaining("app.web.url");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(corsEnvironment)))
        .hasMessageContaining("app.cors.allowed-origins");
  }

  @Test
  void rejectsNonSecureProtectedCookie() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("app.auth.cookie.secure", "false");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("app.auth.cookie.secure");
  }

  @Test
  void rejectsCookiePathCharactersThatResponseCookieCannotEncode() {
    MockEnvironment semicolonEnvironment = validEnvironment();
    semicolonEnvironment.withProperty("app.auth.cookie.path", "/;bad");
    MockEnvironment nonAsciiEnvironment = validEnvironment();
    nonAsciiEnvironment.withProperty("app.auth.cookie.path", "/ż");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(semicolonEnvironment)))
        .hasMessageContaining("app.auth.cookie.path");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(nonAsciiEnvironment)))
        .hasMessageContaining("app.auth.cookie.path");
  }

  @Test
  void rejectsPlaceholderInfrastructureCredentialsAndLoopbackSmtp() {
    MockEnvironment passwordEnvironment = validEnvironment();
    passwordEnvironment.withProperty("spring.datasource.password", "password");
    MockEnvironment smtpEnvironment = validEnvironment();
    smtpEnvironment.withProperty("spring.mail.host", "127.0.0.1");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(passwordEnvironment)))
        .hasMessageContaining("spring.datasource.password");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(smtpEnvironment)))
        .hasMessageContaining("spring.mail.host");
  }

  @Test
  void rejectsMalformedExplicitInfrastructureHosts() {
    MockEnvironment redisEnvironment = validEnvironment();
    redisEnvironment.withProperty("spring.data.redis.host", "https://redis.example.com");
    MockEnvironment smtpEnvironment = validEnvironment();
    smtpEnvironment.withProperty("spring.mail.host", "bad host");
    MockEnvironment invalidIpv4Environment = validEnvironment();
    invalidIpv4Environment.withProperty("spring.data.redis.host", "999.2.3.4");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(redisEnvironment)))
        .hasMessageContaining("spring.data.redis.host");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(smtpEnvironment)))
        .hasMessageContaining("spring.mail.host");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(invalidIpv4Environment)))
        .hasMessageContaining("spring.data.redis.host");
  }

  @Test
  void acceptsValidNonLoopbackIpv6InfrastructureHost() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("spring.data.redis.host", "2001:db8::1");

    assertThatCode(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsInvalidRateLimitPolicyBeforePropertyBinding() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("app.rate-limit.ai-user-minute.window", "500ms");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("app.rate-limit.ai-user-minute.window");
  }

  @Test
  void allowsUnauthenticatedSmtpButRejectsPartialCredentials() {
    MockEnvironment withoutCredentials = validEnvironment();
    withoutCredentials.withProperty("spring.mail.username", "");
    withoutCredentials.withProperty("spring.mail.password", "");
    MockEnvironment partialCredentials = validEnvironment();
    partialCredentials.withProperty("spring.mail.password", "");

    assertThatCode(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(withoutCredentials)))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(partialCredentials)))
        .hasMessageContaining("spring.mail.password");
  }

  @Test
  void rejectsAuthenticatedSmtpWithoutRequiredStarttls() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("spring.mail.properties.mail.smtp.starttls.required", "false");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("spring.mail.properties.mail.smtp.starttls.required");
  }

  @Test
  void rejectsAuthenticatedSmtpWithInvalidTimeout() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("spring.mail.properties.mail.smtp.timeout", "0ms");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("spring.mail.properties.mail.smtp.timeout");
  }

  @Test
  void allowsRateLimitCountsAboveTcpPortRange() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty("app.rate-limit.ai-user-day.max-attempts", "1000000");

    assertThatCode(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsCaseVariantsOfKnownSecretPlaceholders() {
    MockEnvironment environment = validEnvironment();
    environment.withProperty(
        "app.rate-limit.key-secret", "LOCAL-DEV-RATE-LIMIT-KEY-SECRET-CHANGE-ME");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(environment)))
        .hasMessageContaining("app.rate-limit.key-secret");
  }

  @Test
  void rejectsInvalidCookieDomainShapesBeforeCookieCreation() {
    for (String domain : new String[] {"bad domain", ".", "example..com"}) {
      MockEnvironment environment = validEnvironment();
      environment.withProperty("app.auth.cookie.domain", domain);

      assertThatThrownBy(
              () ->
                  ProtectedConfigurationRules.validate(
                      ProtectedConfigurationSnapshot.from(environment)))
          .hasMessageContaining("app.auth.cookie.domain");
    }
  }

  @Test
  void rejectsUrlsThatWouldBreakRedirectsOrBaseRequests() {
    MockEnvironment webPath = validEnvironment();
    webPath.withProperty("app.web.url", "https://app.example.com/unexpected");
    MockEnvironment webQuery = validEnvironment();
    webQuery.withProperty("app.web.url", "https://app.example.com?tenant=one");
    MockEnvironment aiQuery = validEnvironment();
    aiQuery.withProperty("app.ai-service.base-url", "https://ai.example.com?tenant=one");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(ProtectedConfigurationSnapshot.from(webPath)))
        .hasMessageContaining("app.web.url");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(ProtectedConfigurationSnapshot.from(webQuery)))
        .hasMessageContaining("app.web.url");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(ProtectedConfigurationSnapshot.from(aiQuery)))
        .hasMessageContaining("app.ai-service.base-url");
  }

  @Test
  void rejectsMissingDatabaseNameInvalidServerPortAndInvalidFailOpenBoolean() {
    MockEnvironment missingDatabase = validEnvironment();
    missingDatabase.withProperty("spring.datasource.url", "jdbc:postgresql://db.example.com:5432");
    MockEnvironment invalidPort = validEnvironment();
    invalidPort.withProperty("server.port", "0");
    MockEnvironment invalidFailOpen = validEnvironment();
    invalidFailOpen.withProperty("app.rate-limit.fail-open", "maybe");

    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(missingDatabase)))
        .hasMessageContaining("spring.datasource.url");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(invalidPort)))
        .hasMessageContaining("server.port");
    assertThatThrownBy(
            () ->
                ProtectedConfigurationRules.validate(
                    ProtectedConfigurationSnapshot.from(invalidFailOpen)))
        .hasMessageContaining("app.rate-limit.fail-open");
  }

  private static ProtectedConfigurationSnapshot validConfiguration() {
    return ProtectedConfigurationSnapshot.from(validEnvironment());
  }

  static MockEnvironment validEnvironment() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty(
                "spring.datasource.url", "jdbc:postgresql://db.example.com:5432/offertrack")
            .withProperty("spring.datasource.username", "production_user")
            .withProperty("spring.datasource.password", "production-database-password")
            .withProperty("server.port", "8080")
            .withProperty("spring.data.redis.host", "redis.example.com")
            .withProperty("spring.data.redis.port", "6379")
            .withProperty("spring.data.redis.connect-timeout", "2s")
            .withProperty("spring.data.redis.timeout", "2s")
            .withProperty("spring.data.redis.ssl.enabled", "true")
            .withProperty("spring.data.redis.ssl.bundle", "offertrack-redis")
            .withProperty(RedisTlsTrustMaterialValidator.PROPERTY, redisCaCertificate())
            .withProperty("spring.mail.host", "smtp.example.com")
            .withProperty("spring.mail.port", "587")
            .withProperty("spring.mail.username", "smtp-user")
            .withProperty("spring.mail.password", "smtp-password")
            .withProperty("spring.mail.properties.mail.smtp.starttls.enable", "true")
            .withProperty("spring.mail.properties.mail.smtp.starttls.required", "true")
            .withProperty("spring.mail.properties.mail.smtp.connectiontimeout", "5s")
            .withProperty("spring.mail.properties.mail.smtp.timeout", "10s")
            .withProperty("spring.mail.properties.mail.smtp.writetimeout", "10s")
            .withProperty("app.mail.from", "no-reply@example.com")
            .withProperty(
                "spring.security.oauth2.client.registration.google.client-id", "google-client-id")
            .withProperty(
                "spring.security.oauth2.client.registration.google.client-secret",
                "google-client-secret")
            .withProperty("app.jwt.secret", jwtSecret())
            .withProperty("app.jwt.access-token-ttl", "48h")
            .withProperty(
                "app.oauth.authorization-request-cookie-signing-secret",
                "oauth-cookie-signing-secret-which-is-long-enough")
            .withProperty(
                "app.rate-limit.key-secret", "rate-limit-hmac-secret-which-is-long-enough")
            .withProperty("app.rate-limit.fail-open", "false")
            .withProperty("app.web.url", "https://app.example.com")
            .withProperty("app.cors.allowed-origins", "https://app.example.com")
            .withProperty("app.auth.cookie.name", "access_token")
            .withProperty("app.auth.cookie.path", "/")
            .withProperty("app.auth.cookie.domain", ".example.com")
            .withProperty("app.auth.cookie.secure", "true")
            .withProperty("app.auth.cookie.same-site", "None")
            .withProperty("app.ai-service.base-url", "https://ai-service.example.com")
            .withProperty(
                "app.ai-service.internal-api-key", "ai-service-internal-key-which-is-long-enough")
            .withProperty("app.ai-service.auth-mode", "google-id-token")
            .withProperty("app.ai-service.audience", "https://ai-service.example.com")
            .withProperty("server.forward-headers-strategy", "none");

    for (String policy :
        new String[] {
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
          "ai-user-day"
        }) {
      environment.withProperty("app.rate-limit." + policy + ".max-attempts", "10");
      environment.withProperty("app.rate-limit." + policy + ".window", "1h");
    }

    return environment;
  }

  private static String jwtSecret() {
    return "jwt-signing-secret-which-is-at-least-thirty-two-bytes";
  }

  static String redisCaCertificate() {
    return resource("/redis/tls/ca.crt");
  }

  static String redisOtherCaCertificate() {
    return resource("/redis/tls/other-ca.crt");
  }

  private static String resource(String name) {
    try (InputStream input = ProtectedConfigurationRulesTest.class.getResourceAsStream(name)) {
      if (input == null) {
        throw new IllegalStateException("Missing test resource " + name);
      }
      return new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim();
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read test resource " + name, exception);
    }
  }
}
