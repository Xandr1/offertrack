package com.offertrack.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MigrationConfigurationResolverTest {
  private final MigrationConfigurationResolver resolver = new MigrationConfigurationResolver();

  @Test
  void nonProtectedMigrationAcceptsLocalDatabaseConfiguration() {
    var configuration =
        resolver.resolve(
            Map.of(
                "DATABASE_URL",
                "jdbc:postgresql://127.0.0.1/offertrack",
                "DB_USER",
                "test",
                "DB_PASSWORD",
                "test"),
            Map.of(),
            new String[0]);

    assertThat(configuration.url()).isEqualTo("jdbc:postgresql://127.0.0.1/offertrack");
  }

  @ParameterizedTest
  @ValueSource(strings = {"prod", "production", "stage", "staging"})
  void protectedMigrationReusesProtectedDatabasePolicy(String profile) {
    Map<String, String> environment =
        Map.of(
            "SPRING_PROFILES_ACTIVE",
            profile,
            "DATABASE_URL",
            "jdbc:postgresql://127.0.0.1:5432/offertrack",
            "DB_USER",
            "production_user",
            "DB_PASSWORD",
            "production-database-password");

    assertThatThrownBy(() -> resolver.resolve(environment, Map.of(), new String[0]))
        .isInstanceOf(CoreStartupException.class)
        .hasMessageContaining("DATABASE_URL");
  }

  @Test
  void protectedMigrationAcceptsTheSameValidDatabaseShapeAsServerStartup() {
    var configuration =
        resolver.resolve(
            Map.of(
                "SPRING_PROFILES_DEFAULT",
                "staging",
                "DATABASE_URL",
                "jdbc:postgresql://database.example.com:5432/offertrack",
                "DB_USER",
                "production_user",
                "DB_PASSWORD",
                "production-database-password"),
            Map.of(),
            new String[0]);

    assertThat(configuration.username()).isEqualTo("production_user");
  }

  @Test
  void rejectsJvmActiveProfileSourceInsteadOfSilentlyUsingStandardPolicy() {
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    localConfiguration(),
                    Map.of("spring.profiles.active", "production"),
                    new String[0]))
        .isInstanceOf(CoreStartupException.class)
        .hasMessageContaining("UNSUPPORTED_PROFILE_SOURCE")
        .hasMessageContaining("spring.profiles.active")
        .hasMessageContaining("SPRING_PROFILES_ACTIVE")
        .hasMessageNotContaining("local-password");
  }

  @Test
  void rejectsCliActiveProfileSourceInsteadOfSilentlyUsingStandardPolicy() {
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    localConfiguration(),
                    Map.of(),
                    new String[] {"--spring.profiles.active=production"}))
        .isInstanceOf(CoreStartupException.class)
        .hasMessageContaining("UNSUPPORTED_PROFILE_SOURCE")
        .hasMessageContaining("spring.profiles.active")
        .hasMessageContaining("SPRING_PROFILES_ACTIVE");
  }

  @Test
  void conflictingSupportedAndUnsupportedProfileSourcesFailSafely() {
    Map<String, String> environment = new java.util.HashMap<>(localConfiguration());
    environment.put("SPRING_PROFILES_ACTIVE", "production");

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    environment, Map.of("spring.profiles.default", "development"), new String[0]))
        .isInstanceOf(CoreStartupException.class)
        .hasMessageContaining("UNSUPPORTED_PROFILE_SOURCE")
        .hasMessageContaining("spring.profiles.default")
        .hasMessageContaining("SPRING_PROFILES_DEFAULT");
  }

  @Test
  void rejectsCliDefaultProfileSourceWithoutParsingOtherSpringArguments() {
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    localConfiguration(),
                    Map.of(),
                    new String[] {"--unrelated=value", "--spring.profiles.default", "production"}))
        .isInstanceOf(CoreStartupException.class)
        .hasMessageContaining("spring.profiles.default")
        .hasMessageContaining("SPRING_PROFILES_DEFAULT");
  }

  private static Map<String, String> localConfiguration() {
    return Map.of(
        "DATABASE_URL",
        "jdbc:postgresql://127.0.0.1/offertrack",
        "DB_USER",
        "test",
        "DB_PASSWORD",
        "local-password");
  }
}
