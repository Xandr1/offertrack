package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.IdToken;
import com.google.auth.oauth2.IdTokenProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
    AtomicReference<List<IdTokenProvider.Option>> requestedOptions = new AtomicReference<>();
    AtomicReference<String> requestedAudience = new AtomicReference<>();
    IdTokenProvider fakeCredentials =
        (audience, options) -> {
          tokenCalls.incrementAndGet();
          requestedAudience.set(audience);
          requestedOptions.set(options);
          return token();
        };
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
    assertThat(requestedOptions.get()).containsExactly(IdTokenProvider.Option.FORMAT_FULL);
  }

  @Test
  void concurrentRequestsForOneAudienceShareTheCachedRefresh() throws Exception {
    AtomicInteger tokenCalls = new AtomicInteger();
    GoogleAiServiceIdentityTokenProvider provider =
        new GoogleAiServiceIdentityTokenProvider(
            () ->
                (audience, options) -> {
                  tokenCalls.incrementAndGet();
                  return token();
                });
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
}
