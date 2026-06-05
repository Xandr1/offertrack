package com.offertrack.applications.validation;

import java.util.regex.Pattern;

public class JobUrlNormalizer {
  private static final Pattern EXPLICIT_SCHEME_PATTERN =
      Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://");
  private static final Pattern IPV4_HOST_PATTERN =
      Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?$");

  private JobUrlNormalizer() {}

  public static String normalize(String value) {
    if (value == null) {
      return null;
    }

    String trimmedValue = value.trim();
    if (trimmedValue.isEmpty()) {
      return null;
    }

    if (EXPLICIT_SCHEME_PATTERN.matcher(trimmedValue).find() || !looksLikeHostPath(trimmedValue)) {
      return trimmedValue;
    }

    return "https://" + trimmedValue;
  }

  private static boolean looksLikeHostPath(String value) {
    String hostCandidate = value.split("[/?#]", 2)[0];
    if (hostCandidate.isBlank() || hostCandidate.contains(" ")) {
      return false;
    }

    String hostWithoutPort = hostCandidate.replaceFirst(":\\d+$", "");
    if (hostCandidate.contains(":") && hostWithoutPort.equals(hostCandidate)) {
      return false;
    }

    return hostWithoutPort.equalsIgnoreCase("localhost")
        || hostWithoutPort.contains(".")
        || IPV4_HOST_PATTERN.matcher(hostCandidate).matches();
  }
}
