package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class InfrastructureConfigurationRulesTest {
  @Test
  void validatesInfrastructureIndependently() {
    ProtectedConfigurationSnapshot configuration =
        configuration(ProtectedConfigurationRulesTest.validEnvironment());

    assertThatCode(() -> InfrastructureConfigurationRules.validate(configuration))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsMalformedRedisHostWithoutLeakingItsValue() {
    MockEnvironment environment = ProtectedConfigurationRulesTest.validEnvironment();
    environment.withProperty("spring.data.redis.host", "redis host containing secret-marker");

    assertThatThrownBy(() -> InfrastructureConfigurationRules.validate(configuration(environment)))
        .hasMessageContaining("spring.data.redis.host")
        .hasMessageNotContaining("secret-marker");
  }

  @Test
  void protectedServerAndPureMigrationValidationMakeTheSameDatabaseDecisions() {
    for (UnaryOperator<MockEnvironment> invalidCase :
        java.util.List.<UnaryOperator<MockEnvironment>>of(
            environment ->
                environment.withProperty(
                    "spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/offertrack"),
            environment ->
                environment.withProperty(
                    "spring.datasource.url", "jdbc:postgresql://database.example.com/offertrack"),
            environment -> environment.withProperty("spring.datasource.username", "usr"),
            environment -> environment.withProperty("spring.datasource.password", "password"))) {
      MockEnvironment environment =
          invalidCase.apply(ProtectedConfigurationRulesTest.validEnvironment());
      DatabaseConfiguration database =
          new DatabaseConfiguration(
              environment.getProperty("spring.datasource.url"),
              environment.getProperty("spring.datasource.username"),
              environment.getProperty("spring.datasource.password"));

      assertThatThrownBy(
              () ->
                  InfrastructureConfigurationRules.validateDatabase(
                      ProtectedConfigurationSnapshot.from(environment)))
          .as("server validation for %s", database)
          .isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(
              () ->
                  DatabaseConfigurationValidator.validate(
                      database,
                      DatabaseConfigurationValidator.Policy.PROTECTED,
                      DatabaseConfigurationValidator.PropertyNames.migrationEnvironment()))
          .as("migration validation for %s", database)
          .isInstanceOf(DatabaseConfigurationValidationException.class);
    }
  }

  private static ProtectedConfigurationSnapshot configuration(MockEnvironment environment) {
    return ProtectedConfigurationSnapshot.from(environment);
  }
}
