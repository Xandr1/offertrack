package com.offertrack.auth;

/**
 * Bounded diagnostics only: never return a SQL message, SQL text, parameters or constraint detail.
 */
final class AuthDatabaseDiagnostics {
  private AuthDatabaseDiagnostics() {}

  static String sqlState(Throwable error) {
    Throwable current = error;
    for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
      if (current instanceof java.sql.SQLException sql) {
        String state = sql.getSQLState();
        if (state != null && state.matches("[0-9A-Z]{5}")) return state;
      }
    }
    return "unknown";
  }

  static String category(Throwable error) {
    if (error instanceof org.springframework.dao.QueryTimeoutException) return "timeout";
    if (error instanceof org.springframework.dao.DataIntegrityViolationException)
      return "constraint";
    if (error instanceof org.springframework.jdbc.BadSqlGrammarException) return "statement";
    if (error instanceof org.springframework.transaction.TransactionException) return "transaction";
    if (error instanceof org.springframework.dao.DataAccessException
        || error instanceof org.jooq.exception.DataAccessException) return "database";
    return "internal";
  }
}
