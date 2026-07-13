package com.offertrack.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimitSubjectHasherTest {
  @Test
  void derivesStableKeyedHashesWithoutExposingTheSubject() {
    RateLimitProperties properties = new RateLimitProperties();
    properties.setKeySecret("test-rate-limit-key-secret-1234567890");
    RateLimitSubjectHasher hasher = new RateLimitSubjectHasher(properties);

    String first = hasher.hash("sensitive@example.com");
    String second = hasher.hash("sensitive@example.com");

    assertThat(first).isEqualTo(second).hasSize(64).doesNotContain("sensitive@example.com");
  }

  @Test
  void changingTheSecretChangesTheDerivedHash() {
    RateLimitProperties firstProperties = new RateLimitProperties();
    firstProperties.setKeySecret("first-rate-limit-key-secret-123456789");
    RateLimitProperties secondProperties = new RateLimitProperties();
    secondProperties.setKeySecret("second-rate-limit-key-secret-12345678");

    assertThat(new RateLimitSubjectHasher(firstProperties).hash("same-subject"))
        .isNotEqualTo(new RateLimitSubjectHasher(secondProperties).hash("same-subject"));
  }
}
