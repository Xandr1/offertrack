package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdToken;
import com.google.auth.oauth2.IdTokenProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GoogleAiServiceIdentityTokenProviderTest {
  @Test
  void loadsCredentialsLazilyAndCachesOneTokenCredentialPerExactAudience() {
    AtomicInteger loaderCalls = new AtomicInteger();
    AtomicInteger tokenCalls = new AtomicInteger();
    AtomicReference<String> requestedAudience = new AtomicReference<>();
    GoogleCredentials fakeCredentials =
        new FakeGoogleCredentials(
            (audience, options) -> {
              tokenCalls.incrementAndGet();
              requestedAudience.set(audience);
              return token();
            });
    GoogleAiServiceIdentityTokenProvider provider =
        new GoogleAiServiceIdentityTokenProvider(
            () -> {
              loaderCalls.incrementAndGet();
              return fakeCredentials;
            });

    assertThat(loaderCalls).hasValue(0);

    assertThat(provider.getToken("https://ai-service.example.com/")).isEqualTo(tokenValue());
    assertThat(provider.getToken("https://ai-service.example.com/")).isEqualTo(tokenValue());
    assertThat(provider.getToken("https://ai-service.example.com")).isEqualTo(tokenValue());

    assertThat(loaderCalls).hasValue(1);
    assertThat(tokenCalls).hasValue(2);
    assertThat(requestedAudience).hasValue("https://ai-service.example.com");
  }

  @Test
  void concurrentRequestsForOneAudienceShareTheCachedRefresh() throws Exception {
    AtomicInteger tokenCalls = new AtomicInteger();
    GoogleAiServiceIdentityTokenProvider provider =
        new GoogleAiServiceIdentityTokenProvider(
            () ->
                new FakeGoogleCredentials(
                    (audience, options) -> {
                      tokenCalls.incrementAndGet();
                      return token();
                    }));
    List<Callable<String>> requests =
        java.util.stream.IntStream.range(0, 32)
            .mapToObj(
                ignored ->
                    (Callable<String>) () -> provider.getToken("https://ai-service.example.com"))
            .toList();

    try (var executor = Executors.newFixedThreadPool(8)) {
      assertThat(executor.invokeAll(requests))
          .allSatisfy(future -> assertThat(future.get()).isEqualTo(tokenValue()));
    }

    assertThat(tokenCalls).hasValue(1);
  }

  @Test
  void adcLoadingFailureBecomesExpectedIdentityFailureWithoutSensitiveCause() {
    GoogleAiServiceIdentityTokenProvider provider =
        new GoogleAiServiceIdentityTokenProvider(
            () -> {
              throw new IOException("adc-provider-secret");
            });

    assertThatThrownBy(() -> provider.getToken("https://ai-service.example.com"))
        .isExactlyInstanceOf(AiServiceIdentityTokenException.class)
        .hasMessageNotContaining("adc-provider-secret")
        .hasNoCause();
  }

  @Test
  void adcCredentialsWithoutIdTokenSupportBecomeExpectedIdentityFailure() {
    GoogleCredentials accessTokenOnlyCredentials =
        GoogleCredentials.create(
            new AccessToken("access-token-that-must-not-leak", new Date(4_102_444_800_000L)));
    GoogleAiServiceIdentityTokenProvider provider =
        new GoogleAiServiceIdentityTokenProvider(() -> accessTokenOnlyCredentials);

    assertThatThrownBy(() -> provider.getToken("https://ai-service.example.com"))
        .isExactlyInstanceOf(AiServiceIdentityTokenException.class)
        .hasMessageNotContaining("access-token-that-must-not-leak");
  }

  @Test
  void tokenRefreshIoFailureBecomesExpectedIdentityFailure() {
    GoogleAiServiceIdentityTokenProvider provider =
        providerWithTokenSource(
            (audience, options) -> {
              throw new IOException("refresh-provider-secret");
            });

    assertThatThrownBy(() -> provider.getToken("https://ai-service.example.com"))
        .isExactlyInstanceOf(AiServiceIdentityTokenException.class)
        .hasMessageNotContaining("refresh-provider-secret")
        .hasNoCause();
  }

  @Test
  void nullTokenBecomesExpectedIdentityFailure() {
    GoogleAiServiceIdentityTokenProvider provider =
        providerWithTokenSource((audience, options) -> null);

    assertThatThrownBy(() -> provider.getToken("https://ai-service.example.com"))
        .isExactlyInstanceOf(AiServiceIdentityTokenException.class);
  }

  @Test
  void blankTokenBecomesExpectedIdentityFailure() {
    IdToken blankToken = mock(IdToken.class);
    when(blankToken.getTokenValue()).thenReturn(" ");
    GoogleAiServiceIdentityTokenProvider provider =
        providerWithTokenSource((audience, options) -> blankToken);

    assertThatThrownBy(() -> provider.getToken("https://ai-service.example.com"))
        .isExactlyInstanceOf(AiServiceIdentityTokenException.class);
  }

  @Test
  void unexpectedProgrammingFailureIsNotConvertedToCredentialFailure() {
    NullPointerException programmingFailure = new NullPointerException("broken-token-provider");
    GoogleAiServiceIdentityTokenProvider provider =
        providerWithTokenSource(
            (audience, options) -> {
              throw programmingFailure;
            });

    assertThatThrownBy(() -> provider.getToken("https://ai-service.example.com"))
        .isSameAs(programmingFailure);
  }

  private static GoogleAiServiceIdentityTokenProvider providerWithTokenSource(
      IdTokenProvider tokenProvider) {
    return new GoogleAiServiceIdentityTokenProvider(() -> new FakeGoogleCredentials(tokenProvider));
  }

  private static IdToken token() throws IOException {
    return IdToken.create(tokenValue());
  }

  private static String tokenValue() {
    return encode("{\"alg\":\"none\",\"typ\":\"JWT\"}")
        + "."
        + encode("{\"exp\":4102444800,\"sub\":\"offline-test\"}")
        + ".c2lnbmF0dXJl";
  }

  private static String encode(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static final class FakeGoogleCredentials extends GoogleCredentials
      implements IdTokenProvider {
    private final IdTokenProvider tokenProvider;

    private FakeGoogleCredentials(IdTokenProvider tokenProvider) {
      this.tokenProvider = tokenProvider;
    }

    @Override
    public IdToken idTokenWithAudience(String targetAudience, List<Option> options)
        throws IOException {
      return tokenProvider.idTokenWithAudience(targetAudience, options);
    }
  }
}
