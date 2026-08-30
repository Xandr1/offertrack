package com.offertrack.applications;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
final class AiServiceHealthIndicator implements HealthIndicator {
  private final ObjectProvider<HttpAiServiceClient> aiServiceClientProvider;

  AiServiceHealthIndicator(ObjectProvider<HttpAiServiceClient> aiServiceClientProvider) {
    this.aiServiceClientProvider = aiServiceClientProvider;
  }

  @Override
  public Health health() {
    try {
      HttpAiServiceClient aiServiceClient = aiServiceClientProvider.getIfAvailable();
      if (aiServiceClient == null) {
        return Health.down().build();
      }
      return aiServiceClient.isHealthy() ? Health.up().build() : Health.down().build();
    } catch (RuntimeException exception) {
      return Health.down().build();
    }
  }
}
