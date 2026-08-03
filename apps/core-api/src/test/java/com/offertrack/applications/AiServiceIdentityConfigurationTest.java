package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiServiceIdentityConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(AiServiceIdentityConfiguration.class);

  @Test
  void internalKeyModeDoesNotCreateGoogleTokenProvider() {
    contextRunner
        .withPropertyValues("app.ai-service.auth-mode=internal-key")
        .run(context -> assertThat(context).doesNotHaveBean(AiServiceIdentityTokenProvider.class));
  }

  @Test
  void googleProviderConstructionDoesNotLoadApplicationDefaultCredentials() {
    contextRunner
        .withPropertyValues("app.ai-service.auth-mode=google-id-token")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AiServiceIdentityTokenProvider.class);
              assertThat(context.getBean(AiServiceIdentityTokenProvider.class))
                  .isInstanceOf(GoogleAiServiceIdentityTokenProvider.class);
            });
  }
}
