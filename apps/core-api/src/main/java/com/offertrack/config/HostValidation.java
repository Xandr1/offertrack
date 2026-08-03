package com.offertrack.config;

import static com.offertrack.config.ConfigurationRuleSupport.invalid;
import static com.offertrack.config.ConfigurationRuleSupport.invalidException;
import static com.offertrack.config.ConfigurationRuleSupport.requireText;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class HostValidation {
  private static final Pattern HOSTNAME =
      Pattern.compile(
          "(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
              + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*");
  private static final Pattern IPV4_CANDIDATE = Pattern.compile("[0-9.]+");

  private HostValidation() {}

  static URI parseHttpUrl(String value, String property, boolean requireHttps) {
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

  static void requireNonLoopbackHost(String host, String property) {
    requireText(host, property);
    if (isAmbiguousNumericHost(host) || isLoopbackHost(host)) {
      invalid(property);
    }
  }

  static void requireExplicitNonLoopbackHost(String host, String property) {
    requireText(host, property);
    if (!host.equals(host.trim()) || !isExplicitHost(host) || isLoopbackHost(host)) {
      invalid(property);
    }
  }

  static void requireProtectedServiceHost(String host, String property) {
    requireNonLoopbackHost(host, property);
    String normalized = normalizeHost(host);
    if (normalized.endsWith(".local")
        || normalized.endsWith(".localdomain")
        || (isExplicitHostname(normalized) && !normalized.contains("."))) {
      invalid(property);
    }
  }

  static boolean isExplicitHostname(String host) {
    return HOSTNAME.matcher(host).matches();
  }

  static boolean isLoopbackHost(String host) {
    String normalized = normalizeHost(host);
    return normalized.equals("localhost")
        || normalized.endsWith(".localhost")
        || normalized.equals("0.0.0.0")
        || normalized.startsWith("127.")
        || isLocalIpv6Literal(normalized);
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

  private static boolean isExplicitHost(String host) {
    String candidate = host;
    boolean bracketed = candidate.startsWith("[") && candidate.endsWith("]");
    if (bracketed) {
      candidate = candidate.substring(1, candidate.length() - 1);
    }

    if (candidate.contains(":")) {
      try {
        // A colon-bearing candidate can only be an IP literal here, so this parses without DNS.
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

    return isExplicitHostname(candidate);
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
}
