package com.offertrack.runtime;

public record MigrationRunResult(
    boolean successful, int migrationsExecuted, FailureCategory failureCategory) {
  static MigrationRunResult success(int migrationsExecuted) {
    return new MigrationRunResult(true, migrationsExecuted, FailureCategory.NONE);
  }

  static MigrationRunResult failure(FailureCategory failureCategory) {
    return new MigrationRunResult(false, 0, failureCategory);
  }

  public enum FailureCategory {
    NONE,
    DATABASE_CONNECTION_FAILED,
    FLYWAY_VALIDATION_FAILED,
    MIGRATION_FAILED
  }
}
