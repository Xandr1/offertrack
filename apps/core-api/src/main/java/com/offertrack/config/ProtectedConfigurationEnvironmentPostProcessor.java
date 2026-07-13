package com.offertrack.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

public final class ProtectedConfigurationEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {
  public static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER + 1;

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (ProtectedProfiles.isProtected(environment)) {
      ProtectedConfigurationRules.validate(ProtectedConfigurationSnapshot.from(environment));
    }
  }

  @Override
  public int getOrder() {
    return ORDER;
  }
}
