package com.offertrack.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.offertrack.auth.dto.*;
import com.offertrack.users.User;
import com.offertrack.users.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AuthServiceTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-20T10:15:30Z"), ZoneOffset.UTC);
  private static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);
  private static final UUID USER_ID = UUID.randomUUID();
  @Mock UserRepository users;
  @Mock PasswordService passwords;
  @Mock AuthSessionService sessions;
  @Mock UserIdentityRepository identities;
  @Mock AuthTokenService tokens;
  @Mock AuthEmailService mail;
  AuthService service;

  @BeforeEach
  void setup() {
    service = new AuthService(users, passwords, sessions, identities, tokens, mail, CLOCK);
  }

  @Test
  void existingSubjectIgnoresEmailAndUsesLinkedUser() {
    User user = user(true);
    when(identities.findGoogleUser("subject")).thenReturn(Optional.of(USER_ID));
    when(users.lockById(USER_ID)).thenReturn(Optional.of(user));
    var result =
        service.loginWithGoogle(new GoogleIdentity("subject", "changed@example.com", null, false));
    assertThat(result.response().user().email()).isEqualTo(user.email());
    verify(users, never()).lockByEmail(anyString());
    verify(sessions).create(USER_ID);
  }

  @Test
  void unverifiedClaimClearsPasswordInvalidatesTokensAndSessions() {
    when(users.lockByEmail("user@example.com")).thenReturn(Optional.of(user(false)));
    when(users.claimUnverified(USER_ID, NOW))
        .thenReturn(new User(USER_ID, "user@example.com", null, "User", NOW, NOW, NOW));
    when(identities.linkGoogle(USER_ID, "subject", NOW)).thenReturn(true);
    service.loginWithGoogle(
        new GoogleIdentity("subject", " USER@example.com ", "Google User", true));
    var order = inOrder(users, sessions, tokens, identities);
    order.verify(users).claimUnverified(USER_ID, NOW);
    order.verify(sessions).revokeAllLocked(USER_ID, SessionRevocationReason.OAUTH_ACCOUNT_CLAIM);
    order.verify(tokens).consumeAllForUser(USER_ID);
    order.verify(identities).linkGoogle(USER_ID, "subject", NOW);
    order.verify(sessions).create(USER_ID);
  }

  @Test
  void verifiedUserKeepsPasswordAndRejectsSecondGoogleSubject() {
    when(users.lockByEmail("user@example.com")).thenReturn(Optional.of(user(true)));
    when(identities.hasGoogleIdentity(USER_ID)).thenReturn(true);
    assertThatThrownBy(
            () ->
                service.loginWithGoogle(
                    new GoogleIdentity("other", "user@example.com", null, true)))
        .isInstanceOf(AuthenticationRequiredException.class);
    verify(users, never()).claimUnverified(any(), any());
    verifyNoInteractions(sessions);
  }

  @Test
  void rejectsUnverifiedEmailForNewSubject() {
    assertThatThrownBy(
            () ->
                service.loginWithGoogle(new GoogleIdentity("new", "user@example.com", null, false)))
        .isInstanceOf(AuthenticationRequiredException.class);
    verifyNoInteractions(users, sessions);
  }

  @Test
  void normalizesAndBoundsDisplayNameWithoutUsingSubject() {
    when(users.lockByEmail("user@example.com")).thenReturn(Optional.of(user(true)));
    when(identities.linkGoogle(USER_ID, "subject", NOW)).thenReturn(true);
    service.loginWithGoogle(
        new GoogleIdentity("subject", "user@example.com", "  " + "a".repeat(300) + "  ", true));
    verify(users).insertVerifiedOAuthUserIfAbsent("user@example.com", "a".repeat(255), NOW);
  }

  @Test
  void concurrentCredentialChangeRejectsPreviouslyVerifiedPassword() {
    when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user(true)));
    when(passwords.matches("Password1", "password-hash")).thenReturn(true);
    when(users.lockById(USER_ID))
        .thenReturn(
            Optional.of(new User(USER_ID, "user@example.com", null, "User", NOW, NOW, NOW)));
    assertThatThrownBy(() -> service.login(new LoginRequest("user@example.com", "Password1")))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    verifyNoInteractions(sessions);
  }

  @Test
  void verificationSendFailureIsSanitized(CapturedOutput output) {
    when(users.lockByEmail("user@example.com")).thenReturn(Optional.of(user(false)));
    when(tokens.createEmailVerificationToken(USER_ID)).thenReturn("token-marker");
    doThrow(new IllegalStateException("password-marker token-marker"))
        .when(mail)
        .sendVerificationEmail(any(), any());
    assertThatThrownBy(
            () ->
                service.resendVerificationEmail(new ResendVerificationRequest("user@example.com")))
        .isInstanceOf(AuthServiceUnavailableException.class)
        .hasMessageNotContaining("marker");
    assertThat(output.getAll())
        .contains("email_verification_send_failed")
        .doesNotContain("password-marker", "token-marker", USER_ID.toString());
  }

  @Test
  void resetSendFailureRemainsGeneric(CapturedOutput output) {
    when(users.lockByEmail("user@example.com")).thenReturn(Optional.of(user(true)));
    when(tokens.createPasswordResetToken(USER_ID)).thenReturn("reset-marker");
    doThrow(new IllegalStateException("provider-marker"))
        .when(mail)
        .sendPasswordResetEmail(any(), any());
    assertThatCode(() -> service.forgotPassword(new ForgotPasswordRequest("user@example.com")))
        .doesNotThrowAnyException();
    assertThat(output.getAll())
        .contains("password_reset_send_failed")
        .doesNotContain("provider-marker", "reset-marker");
  }

  @Test
  void subjectBoundRejectsRatherThanTruncates() {
    assertThat(new GoogleIdentity("a".repeat(255), null, null, false).subject()).hasSize(255);
    for (String value : new String[] {"", " ", "a".repeat(256), "ż", "\u0000"}) {
      assertThatThrownBy(() -> new GoogleIdentity(value, null, null, false))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  private User user(boolean verified) {
    return new User(
        USER_ID, "user@example.com", "password-hash", "User", verified ? NOW : null, NOW, NOW);
  }
}
