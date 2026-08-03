package com.offertrack;

import com.offertrack.runtime.CoreProcessDispatcher;
import com.offertrack.runtime.CoreProcessResult;
import com.offertrack.runtime.CoreStartupException;
import com.offertrack.runtime.FlywayMigrationRunner;
import com.offertrack.runtime.MigrationRunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CoreApiApplication {
  private static final Logger log = LoggerFactory.getLogger(CoreApiApplication.class);

  public static void main(String[] args) {
    CoreProcessDispatcher dispatcher =
        new CoreProcessDispatcher(
            arguments -> SpringApplication.run(CoreApiApplication.class, arguments),
            new FlywayMigrationRunner());
    try {
      CoreProcessResult result = dispatcher.dispatch(System.getenv(), args);
      if (result.kind() == CoreProcessResult.Kind.MIGRATION_EXIT) {
        reportMigrationResult(result.migrationResult());
        System.exit(result.exitCode());
      }
    } catch (CoreStartupException exception) {
      log.error(
          "core_startup_failed error_category={} property={}",
          exception.category(),
          exception.property());
      System.exit(1);
    }
  }

  private static void reportMigrationResult(MigrationRunResult result) {
    if (result.successful()) {
      log.info("core_migration_succeeded migrations_executed={}", result.migrationsExecuted());
      return;
    }
    log.error("core_migration_failed error_category={}", result.failureCategory());
  }
}
