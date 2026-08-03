package com.offertrack.runtime;

import com.offertrack.config.DatabaseConfiguration;
import java.util.Map;

public final class CoreProcessDispatcher {
  private final ServerLauncher serverLauncher;
  private final MigrationConfigurationResolver migrationConfigurationResolver;
  private final FlywayMigrationRunner migrationRunner;

  public CoreProcessDispatcher(
      ServerLauncher serverLauncher, FlywayMigrationRunner migrationRunner) {
    this(serverLauncher, new MigrationConfigurationResolver(), migrationRunner);
  }

  CoreProcessDispatcher(
      ServerLauncher serverLauncher,
      MigrationConfigurationResolver migrationConfigurationResolver,
      FlywayMigrationRunner migrationRunner) {
    this.serverLauncher = serverLauncher;
    this.migrationConfigurationResolver = migrationConfigurationResolver;
    this.migrationRunner = migrationRunner;
  }

  public CoreProcessResult dispatch(Map<String, String> environment, String[] arguments) {
    CoreRuntimeMode runtimeMode = CoreRuntimeMode.resolve(environment);
    if (runtimeMode == CoreRuntimeMode.SERVER) {
      serverLauncher.start(arguments);
      return CoreProcessResult.serverStarted();
    }

    DatabaseConfiguration databaseConfiguration =
        migrationConfigurationResolver.resolve(environment, System.getProperties(), arguments);
    return CoreProcessResult.migrationCompleted(migrationRunner.run(databaseConfiguration));
  }

  @FunctionalInterface
  public interface ServerLauncher {
    void start(String[] arguments);
  }
}
