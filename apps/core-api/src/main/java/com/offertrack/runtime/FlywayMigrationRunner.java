package com.offertrack.runtime;

import com.offertrack.config.DatabaseConfiguration;
import java.sql.SQLException;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.api.output.MigrateResult;

public final class FlywayMigrationRunner {
  private static final String[] DEFAULT_LOCATIONS = {"classpath:db/migration"};

  private final ClassLoader classLoader;
  private final String[] locations;

  public FlywayMigrationRunner() {
    this(FlywayMigrationRunner.class.getClassLoader(), DEFAULT_LOCATIONS);
  }

  FlywayMigrationRunner(ClassLoader classLoader, String... locations) {
    this.classLoader = classLoader;
    this.locations = Arrays.copyOf(locations, locations.length);
  }

  public MigrationRunResult run(DatabaseConfiguration configuration) {
    try {
      Flyway flyway =
          Flyway.configure(classLoader)
              .dataSource(configuration.url(), configuration.username(), configuration.password())
              .locations(locations)
              .failOnMissingLocations(true)
              .load();
      MigrateResult result = flyway.migrate();
      return MigrationRunResult.success(result.migrationsExecuted);
    } catch (FlywayValidateException exception) {
      return MigrationRunResult.failure(
          MigrationRunResult.FailureCategory.FLYWAY_VALIDATION_FAILED);
    } catch (RuntimeException exception) {
      return MigrationRunResult.failure(
          isConnectionFailure(exception)
              ? MigrationRunResult.FailureCategory.DATABASE_CONNECTION_FAILED
              : MigrationRunResult.FailureCategory.MIGRATION_FAILED);
    }
  }

  private static boolean isConnectionFailure(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SQLException sqlException) {
        String sqlState = sqlException.getSQLState();
        if (sqlState != null && sqlState.startsWith("08")) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }
}
