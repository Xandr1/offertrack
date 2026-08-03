package com.offertrack.applications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AiServiceIdentityConfiguration {
  @Bean
  @ConditionalOnProperty(
      name = "app.ai-service.auth-mode",
      havingValue = "google-id-token",
      matchIfMissing = false)
  AiServiceIdentityTokenProvider aiServiceIdentityTokenProvider() {
    return new GoogleAiServiceIdentityTokenProvider();
  }
}
