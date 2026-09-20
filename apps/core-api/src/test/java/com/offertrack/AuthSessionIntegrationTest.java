package com.offertrack;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.offertrack.auth.*;
import com.offertrack.auth.dto.*;
import com.offertrack.ratelimit.RateLimitGuard;
import com.offertrack.users.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthSessionIntegrationTest {
  @Autowired AuthService auth;
  @Autowired AuthSessionService sessions;
  @Autowired UserRepository users;
  @Autowired AuthTokenService recovery;
  @Autowired PasswordService passwords;
  @Autowired JwtService jwt;
  @Autowired AuthSessionCleanup cleanup;
  @Autowired DSLContext dsl;
  @Autowired MockMvc mvc;
  @MockitoBean RateLimitGuard rateLimits;

  @BeforeEach
  void clean() {
    dsl.execute("delete from application_interviews");
    dsl.execute("delete from job_applications");
    dsl.execute("delete from user_settings");
    dsl.execute("delete from user_auth_tokens");
    dsl.execute("delete from users");
  }

  private AuthService.AuthResult google(String subject, String email) {
    return auth.loginWithGoogle(new GoogleIdentity(subject, email, "User", true));
  }

  private UUID sessionId(SessionTokens tokens) {
    return jwt.verify(tokens.accessToken()).orElseThrow().sessionId();
  }

  private Cookie access(SessionTokens tokens) {
    return new Cookie("access_token", tokens.accessToken());
  }

  private Cookie refresh(SessionTokens tokens) {
    return new Cookie("refresh_token", tokens.refreshToken());
  }

  @Test
  void ownerClaimsAttackerRegistrationAndInvalidatesAllRecoveryTokens() throws Exception {
    var victim = users.createUser("victim@example.com", passwords.hash("Attacker1"), "Victim");
    String oldReset = recovery.createPasswordResetToken(victim.id());
    String oldVerification = recovery.createEmailVerificationToken(victim.id());
    var login = google("owner-subject", "victim@example.com");
    assertThat(login.response().user().id()).isEqualTo(victim.id());
    assertThat(users.findById(victim.id()).orElseThrow().passwordHash()).isNull();
    assertThat(users.findById(victim.id()).orElseThrow().emailVerifiedAt()).isNotNull();
    assertThatThrownBy(() -> auth.login(new LoginRequest("victim@example.com", "Attacker1")))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> auth.resetPassword(new ResetPasswordRequest(oldReset, "Attacker2")))
        .isInstanceOf(InvalidAuthTokenException.class);
    assertThatThrownBy(() -> auth.verifyEmail(new VerifyEmailRequest(oldVerification)))
        .isInstanceOf(InvalidAuthTokenException.class);
    mvc.perform(get("/api/me").cookie(access(login.tokens()))).andExpect(status().isOk());
  }

  @Test
  void linkedSubjectNeverMovesWithChangedEmail() {
    var first = google("subject-a", "first@example.com");
    var other = google("subject-b", "other@example.com");
    var repeated = google("subject-a", "other@example.com");
    assertThat(repeated.response().user().id())
        .isEqualTo(first.response().user().id())
        .isNotEqualTo(other.response().user().id());
    assertThat(repeated.response().user().email()).isEqualTo("first@example.com");
    assertThat(
            auth.loginWithGoogle(new GoogleIdentity("subject-a", null, null, false))
                .response()
                .user()
                .id())
        .isEqualTo(first.response().user().id());
  }

  @Test
  void legacyOAuthUserIsLazilyLinkedAndVerifiedPasswordUserKeepsPassword() {
    var legacy =
        users
            .insertVerifiedOAuthUserIfAbsent("legacy@example.com", "Legacy", OffsetDateTime.now())
            .orElseThrow();
    assertThat(google("legacy-subject", legacy.email()).response().user().id())
        .isEqualTo(legacy.id());
    var existing = users.createUser("password@example.com", passwords.hash("Password1"), "User");
    users.markEmailVerified(existing.id(), OffsetDateTime.now());
    google("password-subject", existing.email());
    assertThat(auth.login(new LoginRequest(existing.email(), "Password1")).tokens().refreshToken())
        .isNotBlank();
    assertThatThrownBy(() -> google("another-subject", existing.email()))
        .isInstanceOf(AuthenticationRequiredException.class);
  }

  @Test
  void passwordAndGoogleCreateSessionsAndOrdinaryRequestsDoNotWriteActivity() throws Exception {
    var first = google("subject", "owner@example.com");
    String reset = recovery.createPasswordResetToken(first.response().user().id());
    auth.resetPassword(new ResetPasswordRequest(reset, "Password1"));
    var login = auth.login(new LoginRequest("owner@example.com", "Password1"));
    UUID id = sessionId(login.tokens());
    var before =
        dsl.fetchOne(
            "select last_refreshed_at, inactivity_expires_at from auth_sessions where id = ?", id);
    mvc.perform(get("/api/me").cookie(access(login.tokens()))).andExpect(status().isOk());
    mvc.perform(get("/api/me").header("Authorization", "Bearer " + login.accessToken()))
        .andExpect(status().isOk());
    assertThat(
            dsl.fetchOne(
                "select last_refreshed_at, inactivity_expires_at from auth_sessions where id = ?",
                id))
        .isEqualTo(before);
    assertThat(
            dsl.fetchOne(
                    "select octet_length(token_hash) from auth_refresh_tokens where session_id = ?",
                    id)
                .get(0, Integer.class))
        .isEqualTo(32);
  }

  @Test
  void refreshRotatesAndReplayCommitsWholeSessionRevocation() throws Exception {
    var initial = google("subject", "owner@example.com").tokens();
    var successor = sessions.refresh(initial.refreshToken()).orElseThrow();
    assertThat(successor.refreshToken()).isNotEqualTo(initial.refreshToken());
    mvc.perform(get("/api/me").cookie(access(successor))).andExpect(status().isOk());
    mvc.perform(post("/auth/refresh").with(csrf()).cookie(refresh(initial)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    // A new request/connection observes the committed rejection, not a rolled-back mutation.
    mvc.perform(get("/api/me").cookie(access(successor))).andExpect(status().isUnauthorized());
    assertThat(sessions.refresh(successor.refreshToken())).isEmpty();
    assertThat(
            dsl.fetchOne(
                    "select revocation_reason from auth_sessions where id = ?", sessionId(initial))
                .get(0, String.class))
        .isEqualTo("refresh_replay");
  }

  @Test
  void concurrentRefreshHasOneSuccessAndRevokesSuccessor() throws Exception {
    var initial = google("subject", "owner@example.com").tokens();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var tasks =
          IntStream.range(0, 2)
              .mapToObj(
                  i ->
                      pool.submit(
                          () -> {
                            start.await();
                            return sessions.refresh(initial.refreshToken());
                          }))
              .toList();
      start.countDown();
      var results =
          List.of(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS));
      assertThat(results.stream().filter(java.util.Optional::isPresent).count()).isEqualTo(1);
      var winner = results.stream().flatMap(java.util.Optional::stream).findFirst().orElseThrow();
      assertThat(sessions.refresh(winner.refreshToken())).isEmpty();
      assertThat(sessions.authenticates(jwt.verify(winner.accessToken()).orElseThrow())).isFalse();
    }
  }

  @Test
  void expiredConsumedTokenStillDetectsReplayDuringActiveSession() {
    var initial = google("subject", "owner@example.com").tokens();
    var successor = sessions.refresh(initial.refreshToken()).orElseThrow();
    dsl.execute(
        "update auth_refresh_tokens set issued_at = now() - interval '2 days', expires_at = now() - interval '1 day' where consumed_at is not null");
    assertThat(sessions.refresh(initial.refreshToken())).isEmpty();
    assertThat(sessions.refresh(successor.refreshToken())).isEmpty();
  }

  @Test
  void expiredOrMismatchedSessionCannotAuthenticateOrRefresh() throws Exception {
    var login = google("subject", "owner@example.com");
    UUID id = sessionId(login.tokens());
    String mismatch =
        jwt.generateAccessToken(UUID.randomUUID(), id, java.time.Instant.now().plusSeconds(900))
            .token();
    mvc.perform(get("/api/me").cookie(new Cookie("access_token", mismatch)))
        .andExpect(status().isUnauthorized());
    dsl.execute(
        """
        update auth_sessions set created_at = now() - interval '10 days',
        last_refreshed_at = now() - interval '8 days', inactivity_expires_at = now() - interval '1 day'
        where id = ?
        """,
        id);
    mvc.perform(get("/api/me").cookie(access(login.tokens()))).andExpect(status().isUnauthorized());
    assertThat(sessions.refresh(login.tokens().refreshToken())).isEmpty();
  }

  @Test
  void sessionLookupFailureFailsClosedWithoutCookiesOrSensitiveError() throws Exception {
    var login = google("subject", "owner@example.com");
    dsl.execute("alter table auth_sessions rename to auth_sessions_unavailable");
    try {
      mvc.perform(get("/api/me").cookie(access(login.tokens())))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.code").value("AUTH_SERVICE_UNAVAILABLE"))
          .andExpect(header().exists("X-Request-Id"))
          .andExpect(cookie().doesNotExist("access_token"));
    } finally {
      dsl.execute("alter table auth_sessions_unavailable rename to auth_sessions");
    }
  }

  @Test
  void concurrentLoginsNeverExceedTenSessions() throws Exception {
    try (var pool = Executors.newFixedThreadPool(12)) {
      var start = new CountDownLatch(1);
      var attempts =
          IntStream.range(0, 16)
              .mapToObj(
                  i ->
                      pool.submit(
                          () -> {
                            start.await();
                            return google("same-subject", "owner@example.com");
                          }))
              .toList();
      start.countDown();
      for (var attempt : attempts) attempt.get(20, TimeUnit.SECONDS);
    }
    assertThat(dsl.fetchOne("select count(*) from users").get(0, Integer.class)).isEqualTo(1);
    assertThat(dsl.fetchOne("select count(*) from user_identities").get(0, Integer.class))
        .isEqualTo(1);
    assertThat(
            dsl.fetchOne("select count(*) from auth_sessions where revoked_at is null")
                .get(0, Integer.class))
        .isEqualTo(10);
    assertThat(
            dsl.fetchOne(
                    "select count(*) from auth_sessions where revocation_reason = 'session_limit'")
                .get(0, Integer.class))
        .isEqualTo(6);
  }

  @Test
  void logoutWorksWithOnlyRefreshAndLogoutAllRevokesEverySession() throws Exception {
    var first = google("subject", "owner@example.com").tokens();
    var second = google("subject", "owner@example.com").tokens();
    mvc.perform(
            post("/auth/logout")
                .with(csrf())
                .cookie(refresh(first), new Cookie("access_token", "expired")))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge("access_token", 0))
        .andExpect(cookie().maxAge("refresh_token", 0));
    assertThat(sessions.refresh(first.refreshToken())).isEmpty();
    mvc.perform(get("/api/me").cookie(access(second))).andExpect(status().isOk());
    var third = google("subject", "owner@example.com").tokens();
    mvc.perform(post("/auth/logout-all").with(csrf()).cookie(access(second), refresh(second)))
        .andExpect(status().isNoContent());
    assertThat(sessions.refresh(third.refreshToken())).isEmpty();
    mvc.perform(get("/api/me").cookie(access(third))).andExpect(status().isUnauthorized());
  }

  @Test
  void resetImmediatelyRevokesEveryIssuedAccessToken() throws Exception {
    var first = google("subject", "owner@example.com");
    var second = google("subject", "owner@example.com");
    String token = recovery.createPasswordResetToken(first.response().user().id());
    auth.resetPassword(new ResetPasswordRequest(token, "NewPassword1"));
    for (var login : List.of(first, second)) {
      mvc.perform(get("/api/me").cookie(access(login.tokens())))
          .andExpect(status().isUnauthorized());
      assertThat(sessions.refresh(login.tokens().refreshToken())).isEmpty();
    }
    assertThat(auth.login(new LoginRequest("owner@example.com", "NewPassword1")).tokens())
        .isNotNull();
  }

  @Test
  void refreshLogoutAndLogoutAllRequireCsrfIncludingRefreshOnlyBrowser() throws Exception {
    var tokens = google("subject", "owner@example.com").tokens();
    for (String path : List.of("/auth/refresh", "/auth/logout", "/auth/logout-all")) {
      mvc.perform(post(path).cookie(refresh(tokens)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
      mvc.perform(
              post(path).cookie(refresh(tokens), access(tokens)).header("X-XSRF-TOKEN", "invalid"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
    mvc.perform(
            post("/auth/refresh")
                .with(csrf())
                .cookie(refresh(tokens), new Cookie("access_token", "expired")))
        .andExpect(status().isNoContent())
        .andExpect(cookie().httpOnly("refresh_token", true))
        .andExpect(cookie().path("refresh_token", "/auth"))
        .andExpect(cookie().path("access_token", "/"));
  }
}
