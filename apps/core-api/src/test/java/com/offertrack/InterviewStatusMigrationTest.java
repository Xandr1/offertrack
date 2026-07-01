package com.offertrack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class InterviewStatusMigrationTest {
  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));

  @Test
  void migratesLegacyStatusesAndEnforcesTheNewDatabaseContract() throws Exception {
    migrateToVersionEight();

    UUID userId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    UUID plannedInterviewId = UUID.randomUUID();
    UUID completedInterviewId = UUID.randomUUID();
    insertLegacyData(userId, applicationId, plannedInterviewId, completedInterviewId);

    migrateToLatest();

    try (Connection connection = connection()) {
      assertThat(readStatus(connection, plannedInterviewId)).isEqualTo("initial");
      assertThat(readStatus(connection, completedInterviewId)).isEqualTo("scheduled");

      UUID defaultedInterviewId = UUID.randomUUID();
      execute(
          connection,
          """
          insert into application_interviews
            (id, user_id, application_id, type, created_at, updated_at)
          values
            ('%s', '%s', '%s', 'technical', now(), now())
          """
              .formatted(defaultedInterviewId, userId, applicationId));
      assertThat(readStatus(connection, defaultedInterviewId)).isEqualTo("initial");

      String constraint =
          readSingleValue(
              connection,
              """
              select pg_get_constraintdef(oid)
              from pg_constraint
              where conname = 'application_interviews_status_check'
              """);
      assertThat(constraint)
          .contains("initial", "scheduled", "passed", "rejected")
          .doesNotContain("planned", "completed");

      assertThatThrownBy(
              () ->
                  insertInterviewWithStatus(
                      connection, UUID.randomUUID(), userId, applicationId, "planned"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () ->
                  insertInterviewWithStatus(
                      connection, UUID.randomUUID(), userId, applicationId, "completed"))
          .isInstanceOf(SQLException.class);

      execute(
          connection,
          "update job_applications set followed_up_at = now() where id = '%s'"
              .formatted(applicationId));
      execute(
          connection,
          "update application_interviews set followed_up_at = now() where id = '%s'"
              .formatted(defaultedInterviewId));
    }
  }

  private static void migrateToVersionEight() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .target(MigrationVersion.fromVersion("8"))
        .load()
        .migrate();
  }

  private static void migrateToLatest() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();
  }

  private static void insertLegacyData(
      UUID userId, UUID applicationId, UUID plannedInterviewId, UUID completedInterviewId)
      throws SQLException {
    try (Connection connection = connection()) {
      execute(
          connection,
          """
          insert into users (id, email, password_hash, name, created_at, updated_at)
          values ('%s', 'migration@example.com', 'hash', 'Migration', now(), now())
          """
              .formatted(userId));
      execute(
          connection,
          """
          insert into job_applications
            (id, user_id, company_name, position_title, stage, created_at, updated_at)
          values
            ('%s', '%s', 'Acme', 'Engineer', 'interviewing', now(), now())
          """
              .formatted(applicationId, userId));
      insertInterviewWithStatus(connection, plannedInterviewId, userId, applicationId, "planned");
      insertInterviewWithStatus(
          connection, completedInterviewId, userId, applicationId, "completed");
    }
  }

  private static void insertInterviewWithStatus(
      Connection connection, UUID interviewId, UUID userId, UUID applicationId, String status)
      throws SQLException {
    execute(
        connection,
        """
        insert into application_interviews
          (id, user_id, application_id, type, status, created_at, updated_at)
        values
          ('%s', '%s', '%s', 'technical', '%s', now(), now())
        """
            .formatted(interviewId, userId, applicationId, status));
  }

  private static String readStatus(Connection connection, UUID interviewId) throws SQLException {
    return readSingleValue(
        connection,
        "select status from application_interviews where id = '%s'".formatted(interviewId));
  }

  private static String readSingleValue(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
