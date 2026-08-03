package com.offertrack.applications;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class AiServiceEndpointNormalizer {
  private AiServiceEndpointNormalizer() {}

  public static NormalizedEndpoint normalizeRoot(String value, String property) {
    if (value == null || value.isBlank() || !value.equals(value.trim())) {
      throw new AiServiceConfigurationException(property);
    }

    try {
      URI uri = new URI(value);
      if (!uri.isAbsolute()
          || !"https".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || uri.getHost().isBlank()
          || uri.getRawUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || !isRootPath(uri.getRawPath())
          || !hasValidAuthority(uri)
          || uri.getPort() == 0
          || uri.getPort() < -1
          || uri.getPort() > 65535) {
        throw new AiServiceConfigurationException(property);
      }

      String host = uri.getHost().toLowerCase(Locale.ROOT);
      int normalizedPort = uri.getPort() == 443 ? -1 : uri.getPort();
      String canonical = "https://" + host + (normalizedPort == -1 ? "" : ":" + normalizedPort);
      return new NormalizedEndpoint(canonical, host, normalizedPort);
    } catch (URISyntaxException exception) {
      throw new AiServiceConfigurationException(property);
    }
  }

  private static boolean isRootPath(String rawPath) {
    return rawPath == null || rawPath.isEmpty() || "/".equals(rawPath);
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

  public record NormalizedEndpoint(String canonicalValue, String host, int port) {}
}
