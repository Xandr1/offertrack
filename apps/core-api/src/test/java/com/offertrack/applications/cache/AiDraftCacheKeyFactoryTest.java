package com.offertrack.applications.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiDraftCacheKeyFactoryTest {
  private final AiDraftCacheKeyFactory factory = new AiDraftCacheKeyFactory();

  @Test
  void normalizesRequiredUrlExamples() {
    assertThat(normalize("https://example.com/jobs/123")).isEqualTo("https://example.com/jobs/123");
    assertThat(normalize("https://EXAMPLE.com/jobs/123#section"))
        .isEqualTo("https://example.com/jobs/123");
    assertThat(normalize("https://example.com/jobs/123?utm_source=linkedin"))
        .isEqualTo("https://example.com/jobs/123");
    assertThat(normalize("https://example.com/jobs/123?gclid=abc&foo=bar"))
        .isEqualTo("https://example.com/jobs/123?foo=bar");
    assertThat(normalize("https://example.com/jobs/123?foo=bar&fbclid=abc"))
        .isEqualTo("https://example.com/jobs/123?foo=bar");
    assertThat(normalize("https://example.com/jobs/123?jobId=42"))
        .isEqualTo("https://example.com/jobs/123?jobId=42");
  }

  @Test
  void removesTrackingParametersCaseInsensitivelyIncludingEncodedNames() {
    assertThat(normalize("https://example.com/jobs/123?UTM_Source=x&%67clid=y&FbClId=z&jobId=42"))
        .isEqualTo("https://example.com/jobs/123?jobId=42");
  }

  @Test
  void preservesMeaningfulQueryOrderDuplicatesAndEncoding() {
    assertThat(normalize("https://example.com/jobs/123?foo=a%20b&foo=c&empty="))
        .isEqualTo("https://example.com/jobs/123?foo=a%20b&foo=c&empty=");
  }

  @Test
  void lowercasesOnlySchemeAndHostAndPreservesPortAndPath() {
    assertThat(normalize("HTTPS://EXAMPLE.COM:8443/Jobs/../Jobs/123%2Fdetail#section"))
        .isEqualTo("https://example.com:8443/Jobs/../Jobs/123%2Fdetail");
  }

  @Test
  void rejectsUrlsWithUserInfoInsteadOfNormalizingCredentials() {
    assertThatThrownBy(() -> factory.create("https://user@example.com/jobs/123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Job URL with userinfo is not cacheable");
    assertThatThrownBy(() -> factory.create("https://user:password@example.com/jobs/123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Job URL with userinfo is not cacheable");
  }

  @Test
  void normalizedUrlContainsOnlyHostAndPortAuthority() {
    assertThat(factory.create("https://EXAMPLE.com:8443/jobs/123").normalizedUrl())
        .isEqualTo("https://example.com:8443/jobs/123")
        .doesNotContain("@");
  }

  @Test
  void producesStableKeysForTrackingVariants() {
    AiDraftCacheKey first =
        factory.create("https://example.com/jobs/123?utm_source=linkedin&gclid=abc");
    AiDraftCacheKey second =
        factory.create("https://EXAMPLE.com/jobs/123?fbclid=another#application");

    assertThat(first.redisKey()).isEqualTo(second.redisKey());
    assertThat(first.redisKey()).matches("offertrack:ai-draft:v1:url-sha256:[0-9a-f]{64}");
    assertThat(first.hashSuffix()).hasSize(12);
  }

  @Test
  void producesDifferentKeysForMeaningfulUrlDifferences() {
    String baseKey = factory.create("https://example.com/jobs/123?jobId=42").redisKey();

    assertThat(factory.create("https://example.com/jobs/123?jobId=43").redisKey())
        .isNotEqualTo(baseKey);
    assertThat(factory.create("https://example.com/jobs/124?jobId=42").redisKey())
        .isNotEqualTo(baseKey);
    assertThat(factory.create("http://example.com/jobs/123?jobId=42").redisKey())
        .isNotEqualTo(baseKey);
    assertThat(factory.create("https://example.com:8443/jobs/123?jobId=42").redisKey())
        .isNotEqualTo(baseKey);
  }

  private String normalize(String url) {
    return factory.normalize(url).value();
  }
}
