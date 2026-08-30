package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Status;

class AiServiceHealthIndicatorTest {
  @Test
  void reportsUpOnlyWhenTheAuthenticatedClientCheckSucceeds() {
    HttpAiServiceClient client = mock(HttpAiServiceClient.class);
    when(client.isHealthy()).thenReturn(true, false);

    AiServiceHealthIndicator indicator = new AiServiceHealthIndicator(provider(client));

    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void reportsDownWhenAReplacementTestBeanRemovesTheConcreteClient() {
    AiServiceHealthIndicator indicator = new AiServiceHealthIndicator(provider(null));

    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<HttpAiServiceClient> provider(HttpAiServiceClient client) {
    ObjectProvider<HttpAiServiceClient> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(client);
    return provider;
  }
}
