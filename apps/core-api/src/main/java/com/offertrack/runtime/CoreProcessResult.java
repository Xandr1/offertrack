package com.offertrack.runtime;

public record CoreProcessResult(Kind kind, int exitCode, MigrationRunResult migrationResult) {
  static CoreProcessResult serverStarted() {
    return new CoreProcessResult(Kind.SERVER_STARTED, 0, null);
  }

  static CoreProcessResult migrationCompleted(MigrationRunResult result) {
    return new CoreProcessResult(Kind.MIGRATION_EXIT, result.successful() ? 0 : 1, result);
  }

  public enum Kind {
    SERVER_STARTED,
    MIGRATION_EXIT
  }
}
