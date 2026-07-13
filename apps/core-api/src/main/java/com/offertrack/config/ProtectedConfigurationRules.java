package com.offertrack.config;

public final class ProtectedConfigurationRules {
  private ProtectedConfigurationRules() {}

  public static void validate(ProtectedConfigurationSnapshot configuration) {
    InfrastructureConfigurationRules.validate(configuration);
    SecretConfigurationRules.validate(configuration);
    WebSecurityConfigurationRules.validate(configuration);
    InfrastructureConfigurationRules.validateForwardHeaders(configuration.forwardHeadersStrategy());
    RateLimitConfigurationRules.validate(configuration);
  }
}
