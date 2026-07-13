package com.offertrack.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.util.StringUtils;

final class ConfigurationRuleSupport {
  private ConfigurationRuleSupport() {}

  static void requireText(String value, String property) {
    if (!StringUtils.hasText(value)) {
      invalid(property);
    }
  }

  static void requireCredential(
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

  static void requireSecret(
      String value, String property, int minimumBytes, String... placeholders) {
    requireText(value, property);
    String normalized = value.trim();
    String placeholderCandidate = normalized.toLowerCase(Locale.ROOT);
    if (normalized.getBytes(StandardCharsets.UTF_8).length < minimumBytes
        || Arrays.stream(placeholders)
            .map(placeholder -> placeholder.toLowerCase(Locale.ROOT))
            .anyMatch(placeholderCandidate::equals)) {
      invalid(property);
    }
  }

  static void requirePositiveDuration(String value, String property) {
    parsePositiveDuration(value, property);
  }

  static Duration parsePositiveDuration(String value, String property) {
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

  static void requirePositiveInteger(String value, String property) {
    requireText(value, property);
    try {
      if (Integer.parseInt(value) < 1) {
        invalid(property);
      }
    } catch (NumberFormatException exception) {
      throw invalidException(property);
    }
  }

  static void requireBoolean(String value, String property) {
    requireText(value, property);
    if (!"true".equalsIgnoreCase(value.trim()) && !"false".equalsIgnoreCase(value.trim())) {
      invalid(property);
    }
  }

  static void requirePort(String value, String property) {
    requireText(value, property);
    try {
      requirePositivePort(Integer.parseInt(value), property);
    } catch (NumberFormatException exception) {
      throw invalidException(property);
    }
  }

  static void requirePositivePort(int port, String property) {
    if (port < 1 || port > 65535) {
      invalid(property);
    }
  }

  static void invalid(String property) {
    throw invalidException(property);
  }

  static IllegalStateException invalidException(String property) {
    return new IllegalStateException(
        "Invalid protected configuration for property '" + property + "'");
  }
}
