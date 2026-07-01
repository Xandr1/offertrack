package com.offertrack.applications.cache;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AiDraftCacheKeyFactory {
  private static final String KEY_PREFIX = "offertrack:ai-draft:v1:url-sha256:";
  private static final int HASH_SUFFIX_LENGTH = 12;

  public AiDraftCacheKey create(String jobUrl) {
    NormalizedUrl normalizedUrl = normalize(jobUrl);
    String hash = sha256(normalizedUrl.value());

    return new AiDraftCacheKey(
        KEY_PREFIX + hash,
        normalizedUrl.value(),
        normalizedUrl.host(),
        hash.substring(hash.length() - HASH_SUFFIX_LENGTH));
  }

  NormalizedUrl normalize(String jobUrl) {
    if (jobUrl == null) {
      throw new IllegalArgumentException("Job URL is required");
    }

    try {
      URI uri = new URI(jobUrl.trim());
      if (uri.getRawUserInfo() != null) {
        throw new IllegalArgumentException("Job URL with userinfo is not cacheable");
      }

      String scheme = uri.getScheme();
      String host = uri.getHost();

      if (!uri.isAbsolute()
          || scheme == null
          || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
          || host == null
          || host.isBlank()) {
        throw new IllegalArgumentException("Job URL must be an absolute HTTP or HTTPS URL");
      }

      String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
      String normalizedHost = host.toLowerCase(Locale.ROOT);
      String normalizedQuery = normalizeQuery(uri.getRawQuery());
      String normalizedValue =
          normalizedScheme
              + "://"
              + normalizedAuthority(uri, normalizedHost)
              + rawPath(uri)
              + (normalizedQuery == null ? "" : "?" + normalizedQuery);

      return new NormalizedUrl(normalizedValue, normalizedHost);
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("Job URL is invalid", exception);
    }
  }

  private static String normalizedAuthority(URI uri, String normalizedHost) {
    StringBuilder authority = new StringBuilder();
    if (normalizedHost.contains(":")
        && !(normalizedHost.startsWith("[") && normalizedHost.endsWith("]"))) {
      authority.append('[').append(normalizedHost).append(']');
    } else {
      authority.append(normalizedHost);
    }

    if (uri.getPort() >= 0) {
      authority.append(':').append(uri.getPort());
    }

    return authority.toString();
  }

  private static String rawPath(URI uri) {
    return uri.getRawPath() == null ? "" : uri.getRawPath();
  }

  private static String normalizeQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isEmpty()) {
      return null;
    }

    String normalizedQuery =
        Arrays.stream(rawQuery.split("&", -1))
            .filter(parameter -> !isTrackingParameter(parameter))
            .collect(Collectors.joining("&"));

    return normalizedQuery.isEmpty() ? null : normalizedQuery;
  }

  private static boolean isTrackingParameter(String rawParameter) {
    int equalsIndex = rawParameter.indexOf('=');
    String rawName = equalsIndex >= 0 ? rawParameter.substring(0, equalsIndex) : rawParameter;
    String decodedName;

    try {
      decodedName = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException exception) {
      decodedName = rawName;
    }

    String normalizedName = decodedName.toLowerCase(Locale.ROOT);
    return normalizedName.startsWith("utm_")
        || normalizedName.equals("gclid")
        || normalizedName.equals("fbclid");
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  record NormalizedUrl(String value, String host) {}
}
