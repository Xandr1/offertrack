package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

class ProtectedConfigurationEnvironmentPostProcessorTest {
  private final ProtectedConfigurationEnvironmentPostProcessor processor =
      new ProtectedConfigurationEnvironmentPostProcessor();

  @ParameterizedTest
  @ValueSource(strings = {"prod", "production", "stage", "staging"})
  void detectsEveryProtectedProfileAlias(String profile) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profile);

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("spring.datasource.url");
  }

  @ParameterizedTest
  @ValueSource(strings = {"prod", "production", "stage", "staging"})
  void detectsProtectedAliasesUsedAsTheDefaultProfile(String profile) {
    MockEnvironment environment = new MockEnvironment();
    environment.setDefaultProfiles(profile);

    assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("spring.datasource.url");
  }

  @Test
  void leavesNonProtectedProfilesUnchanged() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("dev");

    assertThatCode(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
        .doesNotThrowAnyException();
  }

  @Test
  void isRegisteredAndOrderedImmediatelyAfterConfigData() {
    var processorNames =
        SpringFactoriesLoader.loadFactoryNames(
            EnvironmentPostProcessor.class, getClass().getClassLoader());

    assertThat(processorNames)
        .contains(ProtectedConfigurationEnvironmentPostProcessor.class.getName());
    assertThat(processor.getOrder()).isEqualTo(ConfigDataEnvironmentPostProcessor.ORDER + 1);
  }
}
