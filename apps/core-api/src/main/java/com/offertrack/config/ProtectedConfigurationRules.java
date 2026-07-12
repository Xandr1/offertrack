package com.offertrack.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.util.StringUtils;

public final class ProtectedConfigurationRules {
  private static final int MINIMUM_SECRET_BYTES = 32;
  private static final String LOCAL_JWT_SECRET =
      "change-me-change-me-change-me-change-me-change-me";
  private static final String LOCAL_OAUTH_CLIENT_ID = "local-google-client-id";
  private static final String LOCAL_OAUTH_CLIENT_SECRET = "local-google-client-secret";
  private static final String LOCAL_AI_KEY = "local-dev-ai-service-key";
  private static final String LOCAL_RATE_LIMIT_KEY = "local-dev-rate-limit-key-secret-change-me";
  private static final Pattern COOKIE_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
  private static final Pattern COOKIE_DOMAIN =
      Pattern.compile(
          "(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
              + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*");
  private static final Pattern IPV4_CANDIDATE = Pattern.compile("[0-9.]+");
  private static final Set<String> FORWARD_HEADER_STRATEGIES =
      Set.of("none", "framework", "native");

  private ProtectedConfigurationRules() {}

  public static void validate(ProtectedConfigurationSnapshot configuration) {
    validateDatabase(configuration);
    requirePort(configuration.serverPort(), "server.port");
    validateRedis(configuration);
    validateMail(configuration);
    validateGoogle(configuration);
    validateSecrets(configuration);
    validateUrls(configuration);
    validateCookie(configuration);
    validateForwardHeaders(configuration.forwardHeadersStrategy());
    requireBoolean(configuration.rateLimitFailOpen(), "app.rate-limit.fail-open");
    configuration.rateLimitPolicies().forEach(ProtectedConfigurationRules::validateRatePolicy);
  }

  private static void validateDatabase(ProtectedConfigurationSnapshot configuration) {
    URI databaseUri = parseJdbcPostgresUrl(configuration.databaseUrl(), "spring.datasource.url");
    requireNonLoopbackHost(databaseUri.getHost(), "spring.datasource.url");
    requirePositivePort(databaseUri.getPort(), "spring.datasource.url");
    requireCredential(
        configuration.databaseUsername(), "spring.datasource.username", 4, "offertrack");
    requireCredential(
        configuration.databasePassword(),
        "spring.datasource.password",
        12,
        "offertrack",
        "password");
  }

  private static void validateRedis(ProtectedConfigurationSnapshot configuration) {
    requireExplicitNonLoopbackHost(configuration.redisHost(), "spring.data.redis.host");
    requirePort(configuration.redisPort(), "spring.data.redis.port");
    requirePositiveDuration(
        configuration.redisConnectTimeout(), "spring.data.redis.connect-timeout");
    requirePositiveDuration(configuration.redisTimeout(), "spring.data.redis.timeout");
  }

  private static void validateMail(ProtectedConfigurationSnapshot configuration) {
    requireExplicitNonLoopbackHost(configuration.smtpHost(), "spring.mail.host");
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
    requireText(
        configuration.googleClientId(),
        "spring.security.oauth2.client.registration.google.client-id");
    requireText(
        configuration.googleClientSecret(),
        "spring.security.oauth2.client.registration.google.client-secret");

    if (LOCAL_OAUTH_CLIENT_ID.equals(configuration.googleClientId().trim())) {
      invalid("spring.security.oauth2.client.registration.google.client-id");
    }
    if (LOCAL_OAUTH_CLIENT_SECRET.equals(configuration.googleClientSecret().trim())) {
      invalid("spring.security.oauth2.client.registration.google.client-secret");
    }
    requireCredential(
        configuration.googleClientSecret(),
        "spring.security.oauth2.client.registration.google.client-secret",
        16,
        LOCAL_OAUTH_CLIENT_SECRET);
  }

  private static void validateSecrets(ProtectedConfigurationSnapshot configuration) {
    requireSecret(configuration.jwtSecret(), "app.jwt.secret", LOCAL_JWT_SECRET);
    requireSecret(
        configuration.oauthCookieSecret(), "app.oauth.authorization-request-cookie-signing-secret");
    requireSecret(
        configuration.rateLimitKeySecret(), "app.rate-limit.key-secret", LOCAL_RATE_LIMIT_KEY);
    requireSecret(
        configuration.aiServiceInternalApiKey(), "app.ai-service.internal-api-key", LOCAL_AI_KEY);
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

  private static void validateUrls(ProtectedConfigurationSnapshot configuration) {
    URI webUri = parseHttpUrl(configuration.webUrl(), "app.web.url", true);
    requireNonLoopbackHost(webUri.getHost(), "app.web.url");
    if (webUri.getRawQuery() != null
        || webUri.getRawFragment() != null
        || (StringUtils.hasText(webUri.getRawPath()) && !"/".equals(webUri.getRawPath()))) {
      invalid("app.web.url");
    }

    URI aiUri = parseHttpUrl(configuration.aiServiceBaseUrl(), "app.ai-service.base-url", false);
    requireNonLoopbackHost(aiUri.getHost(), "app.ai-service.base-url");
    if (aiUri.getRawQuery() != null || aiUri.getRawFragment() != null) {
      invalid("app.ai-service.base-url");
    }

    requireText(configuration.corsAllowedOrigins(), "app.cors.allowed-origins");
    Arrays.stream(configuration.corsAllowedOrigins().split(",", -1))
        .forEach(ProtectedConfigurationRules::validateCorsOrigin);
  }

  private static void validateCorsOrigin(String origin) {
    URI uri = parseHttpUrl(origin, "app.cors.allowed-origins", true);
    if (!origin.equals("*")
        && uri.getRawUserInfo() == null
        && uri.getRawQuery() == null
        && uri.getRawFragment() == null
        && (uri.getRawPath() == null || uri.getRawPath().isEmpty())) {
      requireNonLoopbackHost(uri.getHost(), "app.cors.allowed-origins");
      return;
    }

    invalid("app.cors.allowed-origins");
  }

  private static void validateCookie(ProtectedConfigurationSnapshot configuration) {
    requireText(configuration.accessCookieName(), "app.auth.cookie.name");
    if (!COOKIE_NAME.matcher(configuration.accessCookieName()).matches()) {
      invalid("app.auth.cookie.name");
    }

    requireText(configuration.accessCookiePath(), "app.auth.cookie.path");
    if (!isValidCookiePath(configuration.accessCookiePath())) {
      invalid("app.auth.cookie.path");
    }

    if (!"true".equalsIgnoreCase(configuration.accessCookieSecure())) {
      invalid("app.auth.cookie.secure");
    }

    requireText(configuration.accessCookieSameSite(), "app.auth.cookie.same-site");
    if (Set.of("strict", "lax", "none").stream()
        .noneMatch(value -> value.equalsIgnoreCase(configuration.accessCookieSameSite()))) {
      invalid("app.auth.cookie.same-site");
    }

    if (StringUtils.hasText(configuration.accessCookieDomain())) {
      String domain = configuration.accessCookieDomain().trim();
      String domainWithoutLeadingDot = domain.startsWith(".") ? domain.substring(1) : domain;
      if (isLoopbackHost(domain) || !COOKIE_DOMAIN.matcher(domainWithoutLeadingDot).matches()) {
        invalid("app.auth.cookie.domain");
      }
    }
  }

  private static void validateForwardHeaders(String value) {
    requireText(value, "server.forward-headers-strategy");
    if (!FORWARD_HEADER_STRATEGIES.contains(value.trim().toLowerCase(Locale.ROOT))) {
      invalid("server.forward-headers-strategy");
    }
  }

  private static void validateRatePolicy(
      ProtectedConfigurationSnapshot.RateLimitPolicyValue policy) {
    requirePositiveInteger(policy.maxAttempts(), policy.propertyPrefix() + ".max-attempts");
    Duration window = parsePositiveDuration(policy.window(), policy.propertyPrefix() + ".window");
    if (window.compareTo(Duration.ofSeconds(1)) < 0) {
      invalid(policy.propertyPrefix() + ".window");
    }
  }

  private static URI parseJdbcPostgresUrl(String value, String property) {
    requireText(value, property);
    if (!value.startsWith("jdbc:postgresql://")) {
      invalid(property);
    }

    try {
      URI uri = new URI(value.substring("jdbc:".length()));
      if (!"postgresql".equalsIgnoreCase(uri.getScheme())
          || !StringUtils.hasText(uri.getHost())
          || uri.getRawUserInfo() != null
          || uri.getRawFragment() != null
          || !StringUtils.hasText(uri.getRawPath())
          || !uri.getRawPath().startsWith("/")
          || uri.getRawPath().length() == 1
          || uri.getRawPath().indexOf('/', 1) >= 0) {
        invalid(property);
      }
      return uri;
    } catch (URISyntaxException exception) {
      throw invalidException(property);
    }
  }

  private static URI parseHttpUrl(String value, String property, boolean requireHttps) {
    requireText(value, property);
    if (!value.equals(value.trim())) {
      invalid(property);
    }
    try {
      URI uri = new URI(value);
      String scheme = uri.getScheme();
      boolean validScheme =
          requireHttps
              ? "https".equalsIgnoreCase(scheme)
              : "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
      if (!validScheme || !uri.isAbsolute() || !StringUtils.hasText(uri.getHost())) {
        invalid(property);
      }
      if (uri.getPort() == 0
          || uri.getPort() < -1
          || uri.getPort() > 65535
          || uri.getRawUserInfo() != null) {
        invalid(property);
      }
      return uri;
    } catch (URISyntaxException exception) {
      throw invalidException(property);
    }
  }

  private static void requireSecret(String value, String property, String... placeholders) {
    requireText(value, property);
    String normalized = value.trim();
    String normalizedPlaceholderCandidate = normalized.toLowerCase(Locale.ROOT);
    if (normalized.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES
        || Arrays.stream(placeholders)
            .map(placeholder -> placeholder.toLowerCase(Locale.ROOT))
            .anyMatch(normalizedPlaceholderCandidate::equals)) {
      invalid(property);
    }
  }

  private static void requireCredential(
      String value, String property, int minimumBytes, String... placeholders) {
    requireText(value, property);
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (normalized.getBytes(StandardCharsets.UTF_8).length < minimumBytes
        || Arrays.stream(placeholders)
            .map(placeholder -> placeholder.toLowerCase(Locale.ROOT))
            .anyMatch(normalized::equals)) {
      invalid(property);
    }
  }

  private static void requirePositiveDuration(String value, String property) {
    parsePositiveDuration(value, property);
  }

  private static Duration parsePositiveDuration(String value, String property) {
    requireText(value, property);
    try {
      Duration duration = DurationStyle.detectAndParse(value.trim());
      if (duration.isZero() || duration.isNegative()) {
        invalid(property);
      }
      return duration;
    } catch (IllegalArgumentException exception) {
      throw invalidException(property);
    }
  }

  private static void requirePositiveInteger(String value, String property) {
    requireText(value, property);
    try {
      if (Integer.parseInt(value) < 1) {
        invalid(property);
      }
    } catch (NumberFormatException exception) {
      throw invalidException(property);
    }
  }

  private static void requireBoolean(String value, String property) {
    requireText(value, property);
    if (!"true".equalsIgnoreCase(value.trim()) && !"false".equalsIgnoreCase(value.trim())) {
      invalid(property);
    }
  }

  private static void requirePort(String value, String property) {
    requireText(value, property);
    try {
      requirePositivePort(Integer.parseInt(value), property);
    } catch (NumberFormatException exception) {
      throw invalidException(property);
    }
  }

  private static void requirePositivePort(int port, String property) {
    if (port < 1 || port > 65535) {
      invalid(property);
    }
  }

  private static void requireNonLoopbackHost(String host, String property) {
    requireText(host, property);
    if (isAmbiguousNumericHost(host) || isLoopbackHost(host)) {
      invalid(property);
    }
  }

  private static boolean isValidCookiePath(String path) {
    if (!path.startsWith("/")) {
      return false;
    }
    for (int index = 0; index < path.length(); index++) {
      char character = path.charAt(index);
      if (character < 0x20 || character > 0x7e || character == ';') {
        return false;
      }
    }
    return true;
  }

  private static void requireExplicitNonLoopbackHost(String host, String property) {
    requireText(host, property);
    if (!host.equals(host.trim()) || !isExplicitHost(host) || isLoopbackHost(host)) {
      invalid(property);
    }
  }

  private static boolean isExplicitHost(String host) {
    String candidate = host;
    boolean bracketed = candidate.startsWith("[") && candidate.endsWith("]");
    if (bracketed) {
      candidate = candidate.substring(1, candidate.length() - 1);
    }

    if (candidate.contains(":")) {
      try {
        InetAddress.getByName(candidate);
        return true;
      } catch (UnknownHostException exception) {
        return false;
      }
    }

    if (bracketed || candidate.startsWith("[") || candidate.endsWith("]")) {
      return false;
    }

    if (IPV4_CANDIDATE.matcher(candidate).matches()) {
      return isCanonicalIpv4(candidate);
    }

    return COOKIE_DOMAIN.matcher(candidate).matches();
  }

  private static boolean isAmbiguousNumericHost(String host) {
    String candidate = host.trim();
    while (candidate.endsWith(".")) {
      candidate = candidate.substring(0, candidate.length() - 1);
    }
    return IPV4_CANDIDATE.matcher(candidate).matches() && !isCanonicalIpv4(candidate);
  }

  private static boolean isCanonicalIpv4(String candidate) {
    String[] octets = candidate.split("\\.", -1);
    if (octets.length != 4) {
      return false;
    }
    try {
      return Arrays.stream(octets)
          .allMatch(
              octet ->
                  !octet.isEmpty()
                      && (octet.length() == 1 || !octet.startsWith("0"))
                      && Integer.parseInt(octet) <= 255);
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private static boolean isLoopbackHost(String host) {
    String normalized = host.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("[") && normalized.endsWith("]")) {
      normalized = normalized.substring(1, normalized.length() - 1);
    }
    while (normalized.endsWith(".")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized.equals("localhost")
        || normalized.endsWith(".localhost")
        || normalized.equals("0.0.0.0")
        || normalized.startsWith("127.")
        || isLocalIpv6Literal(normalized);
  }

  private static boolean isLocalIpv6Literal(String host) {
    if (!host.contains(":")) {
      return false;
    }

    try {
      InetAddress address = InetAddress.getByName(host);
      return address.isLoopbackAddress() || address.isAnyLocalAddress();
    } catch (UnknownHostException exception) {
      return false;
    }
  }

  private static void requireText(String value, String property) {
    if (!StringUtils.hasText(value)) {
      invalid(property);
    }
  }

  private static void invalid(String property) {
    throw invalidException(property);
  }

  private static IllegalStateException invalidException(String property) {
    return new IllegalStateException(
        "Invalid protected configuration for property '" + property + "'");
  }
}
