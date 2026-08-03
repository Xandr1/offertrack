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
                "test"));

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

    assertThatThrownBy(() -> resolver.resolve(environment))
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
                "production-database-password"));

    assertThat(configuration.username()).isEqualTo("production_user");
  }
}
