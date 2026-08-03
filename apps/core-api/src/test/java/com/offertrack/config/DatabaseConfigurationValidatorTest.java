package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatabaseConfigurationValidatorTest {
  @Test
  void databaseConfigurationStringRepresentationRedactsEveryValue() {
    DatabaseConfiguration configuration =
        new DatabaseConfiguration(
            "jdbc:postgresql://database.example.com:5432/private_database",
            "private_username",
            "private_password");

    assertThat(configuration.toString())
        .doesNotContain("private_database", "private_username", "private_password")
        .contains("redacted");
  }

  private static final DatabaseConfigurationValidator.PropertyNames PROPERTIES =
      DatabaseConfigurationValidator.PropertyNames.migrationEnvironment();

  @Test
  void acceptsExplicitPostgresqlConfigurationWithoutProtectedFallbackRules() {
    DatabaseConfiguration configuration =
        new DatabaseConfiguration(
            "jdbc:postgresql://127.0.0.1/offertrack?sslmode=disable", "test", "test");

    assertThatCode(
            () ->
                DatabaseConfigurationValidator.validate(
                    configuration, DatabaseConfigurationValidator.Policy.STANDARD, PROPERTIES))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:mysql://database.example.com:5432/offertrack",
        "postgresql://database.example.com:5432/offertrack",
        "jdbc:postgresql://user:secret@database.example.com:5432/offertrack",
        "jdbc:postgresql://database.example.com:5432/",
        "jdbc:postgresql://database.example.com:5432/one/two",
        "jdbc:postgresql://database.example.com:5432/offertrack?password=secret",
        "jdbc:postgresql://database.example.com:5432/offertrack?u%73er=secret",
        "jdbc:postgresql://database.example.com:5432/offertrack?sslpassword=secret",
        "jdbc:postgresql://database.example.com:5432/offertrack?SSLPassword=secret",
        "jdbc:postgresql://database.example.com:5432/offertrack?ssl%70assword=secret",
        "jdbc:postgresql://database.example.com:5432/offertrack?sslmode=require&sslpassword=secret",
        "jdbc:postgresql://database.example.com:5432/offertrack?sslmode=require;sslpassword=secret",
        "jdbc:postgresql://database.example.com:/offertrack",
        " jdbc:postgresql://database.example.com:5432/offertrack"
      })
  void rejectsNonPostgresqlMissingDatabaseAndCredentialBearingUrls(String url) {
    DatabaseConfiguration configuration =
        new DatabaseConfiguration(url, "production_user", "production-database-password");

    assertThatThrownBy(
            () ->
                DatabaseConfigurationValidator.validate(
                    configuration, DatabaseConfigurationValidator.Policy.STANDARD, PROPERTIES))
        .isInstanceOf(DatabaseConfigurationValidationException.class)
        .hasMessageContaining("DATABASE_URL")
        .hasMessageNotContaining("secret");
  }

  @Test
  void requiresAllExplicitValuesBeforeAnyConnection() {
    assertThatThrownBy(
            () ->
                DatabaseConfigurationValidator.validate(
                    new DatabaseConfiguration(
                        "jdbc:postgresql://database.example.com:5432/offertrack", "user", " "),
                    DatabaseConfigurationValidator.Policy.STANDARD,
                    PROPERTIES))
        .isInstanceOf(DatabaseConfigurationValidationException.class)
        .hasMessageContaining("DB_PASSWORD");
  }

  @Test
  void protectedPolicyPreservesHostPortLengthAndPlaceholderRules() {
    for (DatabaseConfiguration configuration :
        new DatabaseConfiguration[] {
          new DatabaseConfiguration(
              "jdbc:postgresql://127.0.0.1:5432/offertrack",
              "production_user",
              "production-database-password"),
          new DatabaseConfiguration(
              "jdbc:postgresql://database.example.com/offertrack",
              "production_user",
              "production-database-password"),
          new DatabaseConfiguration(
              "jdbc:postgresql://database.example.com:5432/offertrack",
              "usr",
              "production-database-password"),
          new DatabaseConfiguration(
              "jdbc:postgresql://database.example.com:5432/offertrack",
              "production_user",
              "password")
        }) {
      assertThatThrownBy(
              () ->
                  DatabaseConfigurationValidator.validate(
                      configuration, DatabaseConfigurationValidator.Policy.PROTECTED, PROPERTIES))
          .as("configuration %s", configuration)
          .isInstanceOf(DatabaseConfigurationValidationException.class);
    }
  }
}
