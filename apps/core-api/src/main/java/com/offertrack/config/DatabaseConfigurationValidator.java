package com.offertrack.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class DatabaseConfigurationValidator {
  private static final String JDBC_PREFIX = "jdbc:";
  private static final String POSTGRESQL_PREFIX = "jdbc:postgresql://";
  private static final Pattern IPV4_CANDIDATE = Pattern.compile("[0-9.]+");
  private static final Set<String> URL_CREDENTIAL_PROPERTIES = Set.of("user", "password");

  private DatabaseConfigurationValidator() {}

  public static void validate(
      DatabaseConfiguration configuration, Policy policy, PropertyNames properties) {
    URI databaseUri = parseUrl(configuration.url(), properties.url());
    requireText(configuration.username(), properties.username());
    requireText(configuration.password(), properties.password());

    if (policy == Policy.PROTECTED) {
      requireProtectedHost(databaseUri.getHost(), properties.url());
      if (databaseUri.getPort() < 1 || databaseUri.getPort() > 65535) {
        throw invalid(properties.url());
      }
      requireCredential(configuration.username(), properties.username(), 4, "offertrack");
      requireCredential(
          configuration.password(), properties.password(), 12, "offertrack", "password");
    }
  }

  private static URI parseUrl(String value, String property) {
    requireText(value, property);
    if (!value.equals(value.trim()) || !value.startsWith(POSTGRESQL_PREFIX)) {
      throw invalid(property);
    }

    try {
      URI uri = new URI(value.substring(JDBC_PREFIX.length()));
      if (!"postgresql".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || uri.getHost().isBlank()
          || uri.getRawUserInfo() != null
          || uri.getRawFragment() != null
          || !hasDatabaseName(uri.getRawPath())
          || !hasValidAuthority(uri)
          || uri.getPort() == 0
          || uri.getPort() < -1
          || uri.getPort() > 65535
          || containsCredentials(uri.getRawQuery())) {
        throw invalid(property);
      }
      return uri;
    } catch (URISyntaxException | IllegalArgumentException exception) {
      if (exception instanceof DatabaseConfigurationValidationException validationException) {
        throw validationException;
      }
      throw invalid(property);
    }
  }

  private static boolean hasDatabaseName(String rawPath) {
    return rawPath != null
        && rawPath.startsWith("/")
        && rawPath.length() > 1
        && rawPath.indexOf('/', 1) < 0;
  }

  private static boolean hasValidAuthority(URI uri) {
    String authority = uri.getRawAuthority();
    if (authority == null || authority.isEmpty()) {
      return false;
    }
    if (authority.startsWith("[")) {
      int bracket = authority.indexOf(']');
      if (bracket < 1) {
        return false;
      }
      String suffix = authority.substring(bracket + 1);
      return suffix.isEmpty() || suffix.matches(":[0-9]+");
    }
    int colon = authority.lastIndexOf(':');
    return colon < 0 || authority.substring(colon + 1).matches("[0-9]+");
  }

  private static boolean containsCredentials(String rawQuery) {
    if (rawQuery == null) {
      return false;
    }
    for (String parameter : rawQuery.split("[&;]", -1)) {
      String rawName = parameter.split("=", 2)[0];
      String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
      if (URL_CREDENTIAL_PROPERTIES.contains(name)) {
        return true;
      }
    }
    return false;
  }

  private static void requireProtectedHost(String host, String property) {
    String normalized = normalizeHost(host);
    if (isAmbiguousNumericHost(normalized)
        || normalized.equals("localhost")
        || normalized.endsWith(".localhost")
        || normalized.equals("0.0.0.0")
        || normalized.startsWith("127.")
        || isLocalIpv6Literal(normalized)) {
      throw invalid(property);
    }
  }

  private static String normalizeHost(String host) {
    String normalized = host.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("[") && normalized.endsWith("]")) {
      normalized = normalized.substring(1, normalized.length() - 1);
    }
    while (normalized.endsWith(".")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static boolean isAmbiguousNumericHost(String host) {
    return IPV4_CANDIDATE.matcher(host).matches() && !isCanonicalIpv4(host);
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

  private static boolean isLocalIpv6Literal(String host) {
    if (!host.contains(":")) {
      return false;
    }
    try {
      InetAddress address = InetAddress.getByName(host);
      return address.isLoopbackAddress() || address.isAnyLocalAddress();
    } catch (UnknownHostException exception) {
      return true;
    }
  }

  private static void requireText(String value, String property) {
    if (value == null || value.isBlank()) {
      throw invalid(property);
    }
  }

  private static void requireCredential(
      String value, String property, int minimumBytes, String... placeholders) {
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (normalized.getBytes(StandardCharsets.UTF_8).length < minimumBytes
        || Arrays.stream(placeholders)
            .map(placeholder -> placeholder.toLowerCase(Locale.ROOT))
            .anyMatch(normalized::equals)) {
      throw invalid(property);
    }
  }

  private static DatabaseConfigurationValidationException invalid(String property) {
    return new DatabaseConfigurationValidationException(property);
  }

  public enum Policy {
    STANDARD,
    PROTECTED
  }

  public record PropertyNames(String url, String username, String password) {
    public static PropertyNames springDatasource() {
      return new PropertyNames(
          "spring.datasource.url", "spring.datasource.username", "spring.datasource.password");
    }

    public static PropertyNames migrationEnvironment() {
      return new PropertyNames("DATABASE_URL", "DB_USER", "DB_PASSWORD");
    }
  }
}
