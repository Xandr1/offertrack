package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

class RedisTlsConfigurationTest {
  @Test
  void localRedisTlsIsDisabledUnlessExplicitlyConfigured() {
    ConfigurableEnvironment environment = loadConfig();

    assertThat(environment.getProperty("spring.data.redis.ssl.enabled", Boolean.class)).isFalse();
    assertThat(environment.getProperty("spring.data.redis.ssl.bundle")).isNull();
    assertThat(environment.getProperty(RedisTlsTrustMaterialValidator.PROPERTY)).isNull();
  }

  @Test
  void protectedRedisUsesTheScopedPemBundle() {
    ConfigurableEnvironment environment = loadConfig("staging");

    assertThat(environment.getProperty("spring.data.redis.ssl.bundle"))
        .isEqualTo("offertrack-redis");
  }

  private static ConfigurableEnvironment loadConfig(String... profiles) {
    ConfigurableEnvironment environment = new StandardEnvironment();
    environment.setActiveProfiles(profiles);
    ConfigDataEnvironmentPostProcessor.applyTo(environment);
    return environment;
  }
}
