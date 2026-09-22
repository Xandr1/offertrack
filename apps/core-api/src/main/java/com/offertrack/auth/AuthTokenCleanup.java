package com.offertrack.auth;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AuthTokenCleanup {
  private static final Logger log = LoggerFactory.getLogger(AuthTokenCleanup.class);
  private final DSLContext dsl;
  private final Clock clock;
  private final TransactionTemplate transaction;
  private final AtomicLong nextAttempt = new AtomicLong(Long.MIN_VALUE);

  public AuthTokenCleanup(DSLContext dsl, Clock clock, PlatformTransactionManager manager) {
    this.dsl = dsl.configuration().deriveSettings(s -> s.withExecuteLogging(false)).dsl();
    this.clock = clock;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setTimeout(2);
  }

  /** Best effort, independent of session transactions and throttled even after a full batch. */
  public void afterAuthOperation() {
    long now = clock.millis();
    long next = nextAttempt.get();
    if (now < next || !nextAttempt.compareAndSet(next, now + 300_000)) return;
    try {
      transaction.executeWithoutResult(status -> cleanupBatch());
    } catch (RuntimeException exception) {
      log.warn(
          "auth_token_cleanup_failed sql_state={} error_category={}",
          AuthDatabaseDiagnostics.sqlState(exception),
          AuthDatabaseDiagnostics.category(exception));
    }
  }

  int cleanupBatch() {
    dsl.execute("set local statement_timeout = '1000ms'");
    return dsl.execute(
        """
        with candidates as (
          select id from user_auth_tokens
          where least(consumed_at, expires_at) < cast(? as timestamptz)
          order by least(consumed_at, expires_at), id
          limit 100 for update skip locked
        )
        delete from user_auth_tokens token using candidates
        where token.id = candidates.id
        """,
        OffsetDateTime.now(clock).minusDays(30));
  }
}
