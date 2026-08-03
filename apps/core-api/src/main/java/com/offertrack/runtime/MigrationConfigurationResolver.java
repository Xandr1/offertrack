package com.offertrack.runtime;

import com.offertrack.config.DatabaseConfiguration;
import com.offertrack.config.DatabaseConfigurationValidationException;
import com.offertrack.config.DatabaseConfigurationValidator;
import com.offertrack.config.ProtectedProfiles;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

final class MigrationConfigurationResolver {
  DatabaseConfiguration resolve(Map<String, String> environment) {
    DatabaseConfiguration configuration =
        new DatabaseConfiguration(
            environment.get("DATABASE_URL"),
            environment.get("DB_USER"),
            environment.get("DB_PASSWORD"));
    DatabaseConfigurationValidator.Policy policy =
        hasProtectedProfile(environment)
            ? DatabaseConfigurationValidator.Policy.PROTECTED
            : DatabaseConfigurationValidator.Policy.STANDARD;
    try {
      DatabaseConfigurationValidator.validate(
          configuration,
          policy,
          DatabaseConfigurationValidator.PropertyNames.migrationEnvironment());
      return configuration;
    } catch (DatabaseConfigurationValidationException exception) {
      throw new CoreStartupException(
          CoreStartupException.Category.INVALID_DATABASE_CONFIGURATION, exception.property());
    }
  }

  private static boolean hasProtectedProfile(Map<String, String> environment) {
    String profiles = environment.get("SPRING_PROFILES_ACTIVE");
    if (profiles == null || profiles.isBlank()) {
      profiles = environment.get("SPRING_PROFILES_DEFAULT");
    }
    if (profiles == null || profiles.isBlank()) {
      return false;
    }
    return Arrays.stream(profiles.split(",", -1))
        .map(String::trim)
        .map(profile -> profile.toLowerCase(Locale.ROOT))
        .anyMatch(ProtectedProfiles.NAMES::contains);
  }
}
