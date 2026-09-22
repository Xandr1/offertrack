package com.offertrack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offertrack.auth.AuthTokenCleanup;
import com.offertrack.ratelimit.RateLimitGuard;
import com.offertrack.users.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@org.junit.jupiter.api.extension.ExtendWith(
    org.springframework.boot.test.system.OutputCaptureExtension.class)
class AuthTokenCleanupIntegrationTest {
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-22T12:00:00Z");
  @Autowired DSLContext dsl;
  @Autowired UserRepository users;
  @Autowired PlatformTransactionManager manager;
  @Autowired MockMvc mvc;
  @MockitoBean RateLimitGuard rateLimitGuard;
  private UUID userId;

  @BeforeEach
  void setUp() {
    dsl.execute("delete from user_auth_tokens");
    userId = users.createUser(UUID.randomUUID() + "@example.test", "hash", "Cleanup").id();
  }

  private AuthTokenCleanup cleanup(Clock clock) {
    return new AuthTokenCleanup(dsl, clock, manager);
  }

  private Clock clock() {
    return Clock.fixed(NOW.toInstant(), ZoneOffset.UTC);
  }

  private UUID token(OffsetDateTime expires, OffsetDateTime consumed) {
    UUID id = UUID.randomUUID();
    dsl.execute(
        """
        insert into user_auth_tokens(id, user_id, purpose, token_hash, expires_at, consumed_at)
        values (?, ?, 'password_reset', ?, cast(? as timestamptz), cast(? as timestamptz))
        """,
        id,
        userId,
        UUID.randomUUID().toString(),
        expires,
        consumed);
    return id;
  }

  private java.util.List<UUID> remaining() {
    return dsl.fetch("select id from user_auth_tokens order by id").getValues("id", UUID.class);
  }

  @Test
  void retainsActiveAndRecentTerminalTokensAndDeletesOldExpiredOrConsumedTokens() {
    token(NOW.minusDays(31), null);
    token(NOW.plusDays(1), NOW.minusDays(31));
    token(NOW.minusDays(35), NOW.minusDays(2)); // Expiration was the earlier terminal event.
    UUID active = token(NOW.plusDays(1), null);
    UUID recentExpired = token(NOW.minusDays(29), null);
    UUID recentConsumed = token(NOW.plusDays(1), NOW.minusDays(29));
    UUID boundary = token(NOW.minusDays(30), null);
    cleanup(clock()).afterAuthOperation();
    assertThat(remaining())
        .containsExactlyInAnyOrder(active, recentExpired, recentConsumed, boundary);
  }

  @Test
  void batchesAreOrderedBoundedAndThrottledForFiveMinutesEvenWhenFull() {
    for (int index = 0; index < 101; index++) token(NOW.minusDays(40), null);
    var ordered = remaining();
    AtomicReference<Instant> time = new AtomicReference<>(NOW.toInstant());
    Clock mutable =
        new Clock() {
          @Override
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return time.get();
          }
        };
    AuthTokenCleanup cleanup = cleanup(mutable);
    cleanup.afterAuthOperation();
    assertThat(remaining()).containsExactly(ordered.getLast());
    time.set(NOW.toInstant().plusSeconds(299));
    cleanup.afterAuthOperation();
    assertThat(remaining()).hasSize(1);
    time.set(NOW.toInstant().plusSeconds(300));
    cleanup.afterAuthOperation();
    assertThat(remaining()).isEmpty();
  }

  @Test
  void concurrentInstancesSkipLockedRowsWithoutDuplicatingOrBlockingCleanup() throws Exception {
    UUID locked = token(NOW.minusDays(50), null);
    for (int index = 0; index < 200; index++) token(NOW.minusDays(40), null);
    CountDownLatch holding = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    var barrier = new java.util.concurrent.CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(3)) {
      try {
        var holder =
            executor.submit(
                () ->
                    new TransactionTemplate(manager)
                        .executeWithoutResult(
                            status -> {
                              dsl.fetch(
                                  "select id from user_auth_tokens where id = ? for update",
                                  locked);
                              holding.countDown();
                              try {
                                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                              } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(exception);
                              }
                            }));
        assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();
        java.util.concurrent.Callable<Void> run =
            () -> {
              barrier.await(5, TimeUnit.SECONDS);
              cleanup(clock()).afterAuthOperation();
              return null;
            };
        var first = executor.submit(run);
        var second = executor.submit(run);
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        assertThat(remaining()).containsExactly(locked);
        release.countDown();
        holder.get(5, TimeUnit.SECONDS);
        cleanup(clock()).afterAuthOperation();
        assertThat(remaining()).isEmpty();
      } finally {
        release.countDown();
        executor.shutdownNow();
      }
    }
  }

  @Test
  void cleanupFailureDoesNotBreakAuthSuccess(
      org.springframework.boot.test.system.CapturedOutput output) throws Exception {
    // DDL is transaction-scoped and rolled back, forcing only the cleanup query to fail.
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status -> {
              dsl.execute("alter table user_auth_tokens rename to user_auth_tokens_cleanup_test");
              try {
                // REQUIRES_NEW must time out instead of participating in this locked transaction.
                mvc.perform(post("/auth/logout").with(csrf())).andExpect(status().isOk());
              } catch (Exception exception) {
                throw new IllegalStateException(exception);
              } finally {
                status.setRollbackOnly();
              }
            });
    assertThat(output).contains("auth_token_cleanup_failed sql_state=57014 error_category=timeout");
  }
}
