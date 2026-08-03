package com.offertrack.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.offertrack.config.DatabaseConfiguration;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class FlywayMigrationRunnerTest {
  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse(
                      "postgres:16.14-bookworm@sha256:c95fd5346040eba2de3c435e14874af18f5d681fb5848d4f081dbead0878af28")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("offertrack_migration_test")
          .withUsername("migration_test_user")
          .withPassword("migration-test-password-not-for-production");

  @BeforeEach
  void resetSchema() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("drop schema public cascade");
      statement.execute("create schema public");
    }
  }

  @Test
  void appliesAllMigrationsAndSecondRunIsIdempotent() throws SQLException {
    FlywayMigrationRunner runner = new FlywayMigrationRunner();
    DatabaseConfiguration configuration = configuration();

    MigrationRunResult first = runner.run(configuration);
    String firstHistory = historySnapshot();
    String firstSchema = representativeSchemaSnapshot();
    MigrationRunResult second = runner.run(configuration);
    String secondHistory = historySnapshot();
    String secondSchema = representativeSchemaSnapshot();

    assertThat(first.successful()).isTrue();
    assertThat(first.migrationsExecuted()).isGreaterThan(0);
    assertThat(firstHistory).contains("V1__create_users.sql", "V9__dashboard");
    assertThat(firstSchema).contains("users|table", "job_applications|table");
    assertThat(second.successful()).isTrue();
    assertThat(second.migrationsExecuted()).isZero();
    assertThat(secondHistory).isEqualTo(firstHistory);
    assertThat(secondSchema).isEqualTo(firstSchema);
  }

  @Test
  void dispatcherMigratesWithDatabaseOnlyConfigurationAndNeverLaunchesServer() throws SQLException {
    AtomicInteger serverLaunches = new AtomicInteger();
    CoreProcessDispatcher dispatcher =
        new CoreProcessDispatcher(
            arguments -> serverLaunches.incrementAndGet(), new FlywayMigrationRunner());
    Map<String, String> environment = migrationEnvironment();

    CoreProcessResult result = dispatcher.dispatch(environment, new String[0]);

    assertThat(result.kind()).isEqualTo(CoreProcessResult.Kind.MIGRATION_EXIT);
    assertThat(result.exitCode()).isZero();
    assertThat(result.migrationResult().successful()).isTrue();
    assertThat(serverLaunches).hasValue(0);
    assertThat(representativeSchemaSnapshot()).contains("users|table");
  }

  @Test
  void directMigrationIgnoresSpringFlywayDisabled() {
    AtomicInteger serverLaunches = new AtomicInteger();
    CoreProcessDispatcher dispatcher =
        new CoreProcessDispatcher(
            arguments -> serverLaunches.incrementAndGet(), new FlywayMigrationRunner());
    Map<String, String> environment = new HashMap<>(migrationEnvironment());
    environment.put("SPRING_FLYWAY_ENABLED", "false");

    CoreProcessResult result = dispatcher.dispatch(environment, new String[0]);

    assertThat(result.exitCode()).isZero();
    assertThat(result.migrationResult().migrationsExecuted()).isGreaterThan(0);
    assertThat(serverLaunches).hasValue(0);
  }

  @Test
  void unreachableDatabaseReturnsSafeConnectionFailure(CapturedOutput output) throws IOException {
    int port = unusedPort();
    String password = "password-that-must-not-appear";
    MigrationRunResult result =
        new FlywayMigrationRunner()
            .run(
                new DatabaseConfiguration(
                    "jdbc:postgresql://127.0.0.1:" + port + "/offertrack", "offertrack", password));

    assertThat(result.successful()).isFalse();
    assertThat(result.failureCategory())
        .isEqualTo(MigrationRunResult.FailureCategory.DATABASE_CONNECTION_FAILED);
    assertThat(output).doesNotContain(password);
  }

  @Test
  void brokenMigrationReturnsFailedResultWithoutPasswordLeak(CapturedOutput output) {
    String password = POSTGRES.getPassword();
    FlywayMigrationRunner runner =
        new FlywayMigrationRunner(getClass().getClassLoader(), "classpath:db/broken-migration");

    MigrationRunResult result = runner.run(configuration());

    assertThat(result.successful()).isFalse();
    assertThat(result.failureCategory())
        .isEqualTo(MigrationRunResult.FailureCategory.MIGRATION_FAILED);
    assertThat(output).doesNotContain(password);
  }

  @Test
  void changedAppliedMigrationProducesValidationFailure() throws SQLException {
    FlywayMigrationRunner runner = new FlywayMigrationRunner();
    assertThat(runner.run(configuration()).successful()).isTrue();
    execute("update flyway_schema_history set checksum = checksum + 1 where version = '1'");

    MigrationRunResult result = runner.run(configuration());

    assertThat(result.successful()).isFalse();
    assertThat(result.failureCategory())
        .isEqualTo(MigrationRunResult.FailureCategory.FLYWAY_VALIDATION_FAILED);
  }

  @Test
  void missingDatabaseConfigurationFailsBeforeMigration() {
    CoreProcessDispatcher dispatcher =
        new CoreProcessDispatcher(arguments -> {}, new FlywayMigrationRunner());

    assertThatThrownBy(
            () ->
                dispatcher.dispatch(
                    Map.of(
                        "OFFERTRACK_RUN_MODE",
                        "migrate",
                        "DATABASE_URL",
                        POSTGRES.getJdbcUrl(),
                        "DB_USER",
                        POSTGRES.getUsername()),
                    new String[0]))
        .isInstanceOf(CoreStartupException.class)
        .hasMessageContaining("DB_PASSWORD")
        .hasMessageNotContaining(POSTGRES.getUsername());
    assertThat(historyTableExists()).isFalse();
  }

  private static Map<String, String> migrationEnvironment() {
    return Map.of(
        "OFFERTRACK_RUN_MODE",
        "migrate",
        "DATABASE_URL",
        POSTGRES.getJdbcUrl(),
        "DB_USER",
        POSTGRES.getUsername(),
        "DB_PASSWORD",
        POSTGRES.getPassword());
  }

  private static DatabaseConfiguration configuration() {
    return new DatabaseConfiguration(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static String historySnapshot() throws SQLException {
    return queryLines(
        """
        select installed_rank, version, description, type, script, checksum, success
        from flyway_schema_history
        order by installed_rank
        """);
  }

  private static String representativeSchemaSnapshot() throws SQLException {
    return queryLines(
        """
        select table_name, 'table'
        from information_schema.tables
        where table_schema = 'public'
          and table_name in ('users', 'job_applications', 'application_interviews')
        order by table_name
        """);
  }

  private static String queryLines(String sql) throws SQLException {
    StringBuilder snapshot = new StringBuilder();
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      int columns = result.getMetaData().getColumnCount();
      while (result.next()) {
        for (int index = 1; index <= columns; index++) {
          if (index > 1) {
            snapshot.append('|');
          }
          snapshot.append(result.getString(index));
        }
        snapshot.append('\n');
      }
    }
    return snapshot.toString();
  }

  private static boolean historyTableExists() {
    try {
      return !queryLines("select to_regclass('public.flyway_schema_history')").startsWith("null");
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void execute(String sql) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static int unusedPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
