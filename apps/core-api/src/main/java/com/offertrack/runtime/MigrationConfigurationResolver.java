package com.offertrack.runtime;

import com.offertrack.config.DatabaseConfiguration;
import com.offertrack.config.DatabaseConfigurationValidationException;
import com.offertrack.config.DatabaseConfigurationValidator;
import com.offertrack.config.ProtectedProfiles;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

final class MigrationConfigurationResolver {
  private static final ProfileSource ACTIVE_PROFILES =
      new ProfileSource(
          "spring.profiles.active", "SPRING_PROFILES_ACTIVE", "--spring.profiles.active");
  private static final ProfileSource DEFAULT_PROFILES =
      new ProfileSource(
          "spring.profiles.default", "SPRING_PROFILES_DEFAULT", "--spring.profiles.default");

  DatabaseConfiguration resolve(
      Map<String, String> environment, Map<?, ?> systemProperties, String[] arguments) {
    rejectUnsupportedProfileSources(systemProperties, arguments);

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

  private static void rejectUnsupportedProfileSources(
      Map<?, ?> systemProperties, String[] arguments) {
    for (ProfileSource source : new ProfileSource[] {ACTIVE_PROFILES, DEFAULT_PROFILES}) {
      if (systemProperties.containsKey(source.systemProperty())) {
        throw unsupportedProfileSource(source);
      }
      for (String argument : arguments) {
        if (argument.equals(source.commandLineOption())
            || argument.startsWith(source.commandLineOption() + "=")) {
          throw unsupportedProfileSource(source);
        }
      }
    }
  }

  private static CoreStartupException unsupportedProfileSource(ProfileSource source) {
    return new CoreStartupException(
        CoreStartupException.Category.UNSUPPORTED_PROFILE_SOURCE,
        source.systemProperty() + " (use " + source.environmentVariable() + ")");
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

  private record ProfileSource(
      String systemProperty, String environmentVariable, String commandLineOption) {}
}
