package com.offertrack.auth;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AuthSessionCleanup {
  private static final Logger log = LoggerFactory.getLogger(AuthSessionCleanup.class);
  private final DSLContext dsl;
  private final Clock clock;
  private final TransactionTemplate transaction;
  private final AtomicLong nextAttempt = new AtomicLong(Long.MIN_VALUE);

  public AuthSessionCleanup(DSLContext dsl, Clock clock, PlatformTransactionManager manager) {
    this.dsl = dsl.configuration().deriveSettings(s -> s.withExecuteLogging(false)).dsl();
    this.clock = clock;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setTimeout(2);
  }

  /** Call after the auth transaction has completed, never from ordinary authentication reads. */
  public void afterAuthOperation() {
    long now = clock.millis();
    long next = nextAttempt.get();
    if (now < next || !nextAttempt.compareAndSet(next, now + 300_000)) return;
    try {
      transaction.executeWithoutResult(status -> cleanup());
    } catch (RuntimeException exception) {
      log.warn(
          "auth_session_cleanup_failed sql_state={} error_category={}",
          AuthDatabaseDiagnostics.sqlState(exception),
          AuthDatabaseDiagnostics.category(exception));
    }
  }

  private void cleanup() {
    dsl.execute("set local statement_timeout = '1000ms'");
    Boolean acquired =
        dsl.fetchOne("select pg_try_advisory_xact_lock(?, ?)", 186989, 1).get(0, Boolean.class);
    if (!Boolean.TRUE.equals(acquired)) return;
    var parents =
        dsl.fetch(
            """
        select id from auth_sessions
        where least(revoked_at, inactivity_expires_at, absolute_expires_at) < cast(? as timestamptz)
        order by least(revoked_at, inactivity_expires_at, absolute_expires_at), id
        limit 20 for update skip locked
        """,
            OffsetDateTime.now(clock).minusDays(30));
    int remaining = 100;
    long deadline = System.nanoTime() + 1_000_000_000L;
    for (var parent : parents) {
      if (remaining == 0 || System.nanoTime() >= deadline) break;
      UUID id = parent.get("id", UUID.class);
      int deleted =
          dsl.execute(
              """
          delete from auth_refresh_tokens where id in (
            select id from auth_refresh_tokens where session_id = ?
            order by id limit ? for update skip locked)
          """,
              id,
              remaining);
      remaining -= deleted;
      // The parent lock excludes concurrent token issuance; never cascade a nonempty parent.
      dsl.execute(
          """
          delete from auth_sessions where id = ?
          and not exists(select 1 from auth_refresh_tokens where session_id = ?)
          """,
          id,
          id);
    }
  }
}
