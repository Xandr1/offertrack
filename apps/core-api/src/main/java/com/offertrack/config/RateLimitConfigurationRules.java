package com.offertrack.config;

import static com.offertrack.config.ConfigurationRuleSupport.invalid;
import static com.offertrack.config.ConfigurationRuleSupport.parsePositiveDuration;
import static com.offertrack.config.ConfigurationRuleSupport.requireBoolean;
import static com.offertrack.config.ConfigurationRuleSupport.requirePositiveInteger;

import java.time.Duration;

final class RateLimitConfigurationRules {
  private RateLimitConfigurationRules() {}

  static void validate(ProtectedConfigurationSnapshot configuration) {
    requireBoolean(configuration.rateLimitFailOpen(), "app.rate-limit.fail-open");
    configuration.rateLimitPolicies().forEach(RateLimitConfigurationRules::validatePolicy);
  }

  private static void validatePolicy(ProtectedConfigurationSnapshot.RateLimitPolicyValue policy) {
    requirePositiveInteger(policy.maxAttempts(), policy.propertyPrefix() + ".max-attempts");
    Duration window = parsePositiveDuration(policy.window(), policy.propertyPrefix() + ".window");
    if (window.compareTo(Duration.ofSeconds(1)) < 0) {
      invalid(policy.propertyPrefix() + ".window");
    }
  }
}
