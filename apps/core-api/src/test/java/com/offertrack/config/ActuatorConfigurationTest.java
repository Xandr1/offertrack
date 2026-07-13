package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

class ActuatorConfigurationTest {
  @Test
  void localConfigurationExposesOnlyHealthWithProbesAndNoDetails() {
    ConfigurableEnvironment environment = loadConfig();

    assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
        .isEqualTo("health");
    assertThat(environment.getProperty("management.endpoint.health.show-details"))
        .isEqualTo("never");
    assertThat(environment.getProperty("management.endpoint.health.probes.enabled", Boolean.class))
        .isTrue();
    assertThat(environment.getProperty("management.endpoint.health.group.liveness.include"))
        .isEqualTo("livenessState");
    assertThat(environment.getProperty("management.endpoint.health.group.readiness.include"))
        .isEqualTo("readinessState");
  }

  @ParameterizedTest
  @ValueSource(strings = {"prod", "production", "stage", "staging", "e2e"})
  void deploymentReadinessIncludesDatabaseAndRedis(String profile) {
    ConfigurableEnvironment environment = loadConfig(profile);

    assertThat(environment.getProperty("management.endpoint.health.group.readiness.include"))
        .isEqualTo("readinessState,db,redis");
  }

  private static ConfigurableEnvironment loadConfig(String... profiles) {
    ConfigurableEnvironment environment = new StandardEnvironment();
    environment.setActiveProfiles(profiles);
    ConfigDataEnvironmentPostProcessor.applyTo(environment);
    return environment;
  }
}
