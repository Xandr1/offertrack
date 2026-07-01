package com.offertrack.applications.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpOrHttpsUrlValidatorTest {
  private final HttpOrHttpsUrlValidator validator = new HttpOrHttpsUrlValidator();

  @Test
  void allowsNull() {
    assertThat(validator.isValid(null, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://example.com/jobs/123",
        "http://example.com/jobs/123",
        "https://example.com/jobs/123?jobId=42"
      })
  void acceptsHttpAndHttpsUrlsWithoutUserInfo(String url) {
    assertThat(validator.isValid(url, null)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ftp://example.com/jobs/123",
        "https://user@example.com/jobs/123",
        "https://user:password@example.com/jobs/123",
        "http://token@example.com/jobs/123"
      })
  void rejectsUnsupportedSchemesAndUserInfo(String url) {
    assertThat(validator.isValid(url, null)).isFalse();
  }
}
