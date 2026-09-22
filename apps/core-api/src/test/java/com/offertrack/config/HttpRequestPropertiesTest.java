package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class HttpRequestPropertiesTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(HttpRequestProperties.class)
  static class Config {}

  @Test
  void defaultsTo256KiBAndAcceptsExplicitPositiveLimit() {
    runner.run(
        context ->
            assertThat(context.getBean(HttpRequestProperties.class).getMaxRequestBodyBytes())
                .isEqualTo(262144));
    runner
        .withPropertyValues("app.http.max-request-body-bytes=1024")
        .run(
            context ->
                assertThat(context.getBean(HttpRequestProperties.class).getMaxRequestBodyBytes())
                    .isEqualTo(1024));
  }

  @Test
  void invalidLimitsFailStartup() {
    for (String value : new String[] {"0", "-1", "invalid"}) {
      runner
          .withPropertyValues("app.http.max-request-body-bytes=" + value)
          .run(context -> assertThat(context).hasFailed());
    }
  }
}
