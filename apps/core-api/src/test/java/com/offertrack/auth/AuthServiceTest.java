package com.offertrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AuthServiceTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-13T10:15:30Z"), ZoneOffset.UTC);
  private static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);
  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Mock private UserRepository userRepository;
  @Mock private PasswordService passwordService;
  @Mock private JwtService jwtService;
  @Mock private AuthTokenService authTokenService;
  @Mock private AuthEmailService authEmailService;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(
            userRepository, passwordService, jwtService, authTokenService, authEmailService, CLOCK);
  }

  @Test
  void googleLoginUsesInsertedUserWhenInsertSucceeds() {
    User insertedUser = verifiedUser("new@example.com", "Google User");

    when(userRepository.insertVerifiedOAuthUserIfAbsent("new@example.com", "Google User", NOW))
        .thenReturn(Optional.of(insertedUser));
    when(jwtService.generateAccessToken(USER_ID, "new@example.com")).thenReturn("jwt-token");

    AuthService.AuthResult result =
        authService.loginWithGoogle(" New@Example.COM ", "Google User", true);

    assertThat(result.accessToken()).isEqualTo("jwt-token");
    assertThat(result.response().user().id()).isEqualTo(USER_ID);
    assertThat(result.response().user().email()).isEqualTo("new@example.com");
    verify(userRepository, never()).findByEmail(anyString());
  }

  @Test
  void googleLoginFetchesExistingVerifiedUserWhenInsertDoesNothing() {
    User existingUser = verifiedUser("race@example.com", "Race User");

    when(userRepository.insertVerifiedOAuthUserIfAbsent("race@example.com", "Race User", NOW))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail("race@example.com")).thenReturn(Optional.of(existingUser));
    when(jwtService.generateAccessToken(USER_ID, "race@example.com")).thenReturn("jwt-token");

    AuthService.AuthResult result =
        authService.loginWithGoogle("Race@Example.com", "Race User", true);

    assertThat(result.accessToken()).isEqualTo("jwt-token");
    assertThat(result.response().user().id()).isEqualTo(USER_ID);
    verify(userRepository, never()).markEmailVerified(any(UUID.class), any(OffsetDateTime.class));
  }

  @Test
  void googleLoginMarksExistingUnverifiedUserVerifiedAndRefetches() {
    User unverifiedUser =
        new User(
            USER_ID,
            "unverified@example.com",
            "password-hash",
            "Existing User",
            null,
            NOW.minusDays(1),
            NOW.minusDays(1));
    User verifiedUser =
        new User(
            USER_ID,
            "unverified@example.com",
            "password-hash",
            "Existing User",
            NOW,
            NOW.minusDays(1),
            NOW);

    when(userRepository.insertVerifiedOAuthUserIfAbsent(
            "unverified@example.com", "Google User", NOW))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail("unverified@example.com"))
        .thenReturn(Optional.of(unverifiedUser), Optional.of(verifiedUser));
    when(jwtService.generateAccessToken(USER_ID, "unverified@example.com")).thenReturn("jwt-token");

    AuthService.AuthResult result =
        authService.loginWithGoogle("unverified@example.com", "Google User", true);

    assertThat(result.accessToken()).isEqualTo("jwt-token");
    assertThat(result.response().user().id()).isEqualTo(USER_ID);
    verify(userRepository).markEmailVerified(USER_ID, NOW);
  }

  @Test
  void googleLoginTrimsDisplayNameBeforeInsert() {
    assertGoogleDisplayNameNormalized("  Google User  ", "Google User");
  }

  @Test
  void googleLoginStoresBlankDisplayNameAsNull() {
    assertGoogleDisplayNameNormalized("   ", null);
  }

  @Test
  void googleLoginCapsDisplayNameBeforeInsert() {
    assertGoogleDisplayNameNormalized("a".repeat(300), "a".repeat(255));
  }

  @Test
  void verificationSendFailureIsSafelyLoggedAndStillPropagated(CapturedOutput output) {
    User user = unverifiedUser("verify@example.com");
    when(userRepository.findByEmail("verify@example.com")).thenReturn(Optional.of(user));
    when(authTokenService.createEmailVerificationToken(USER_ID))
        .thenReturn("email-verification-token-marker");
    org.mockito.Mockito.doThrow(
            new IllegalStateException(
                "provider-message-marker smtp-password-marker email-verification-token-marker"))
        .when(authEmailService)
        .sendVerificationEmail(user, "email-verification-token-marker");

    assertThatThrownBy(
            () ->
                authService.resendVerificationEmail(
                    new com.offertrack.auth.dto.ResendVerificationRequest("verify@example.com")))
        .isInstanceOf(IllegalStateException.class);

    assertThat(output.getOut())
        .contains("email_verification_send_failed error_type=IllegalStateException")
        .doesNotContain(
            USER_ID.toString(),
            "provider-message-marker",
            "smtp-password-marker",
            "email-verification-token-marker");
  }

  @Test
  void passwordResetSendFailureIsSafelyLoggedAndStillSwallowed(CapturedOutput output) {
    User user = verifiedUser("reset@example.com", "Reset User");
    when(userRepository.findByEmail("reset@example.com")).thenReturn(Optional.of(user));
    when(authTokenService.createPasswordResetToken(USER_ID))
        .thenReturn("password-reset-token-marker");
    org.mockito.Mockito.doThrow(
            new IllegalStateException(
                "provider-message-marker smtp-password-marker password-reset-token-marker"))
        .when(authEmailService)
        .sendPasswordResetEmail(user, "password-reset-token-marker");

    assertThatCode(
            () ->
                authService.forgotPassword(
                    new com.offertrack.auth.dto.ForgotPasswordRequest("reset@example.com")))
        .doesNotThrowAnyException();

    assertThat(output.getOut())
        .contains("password_reset_send_failed error_type=IllegalStateException")
        .doesNotContain(
            USER_ID.toString(),
            "provider-message-marker",
            "smtp-password-marker",
            "password-reset-token-marker");
  }

  private void assertGoogleDisplayNameNormalized(String inputName, String expectedName) {
    User insertedUser = verifiedUser("name@example.com", expectedName);
    ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);

    when(userRepository.insertVerifiedOAuthUserIfAbsent(
            eq("name@example.com"), nameCaptor.capture(), eq(NOW)))
        .thenReturn(Optional.of(insertedUser));
    when(jwtService.generateAccessToken(USER_ID, "name@example.com")).thenReturn("jwt-token");

    AuthService.AuthResult result =
        authService.loginWithGoogle("Name@Example.com", inputName, true);

    assertThat(result.accessToken()).isEqualTo("jwt-token");
    assertThat(nameCaptor.getValue()).isEqualTo(expectedName);
  }

  private static User verifiedUser(String email, String name) {
    return new User(USER_ID, email, null, name, NOW, NOW, NOW);
  }

  private static User unverifiedUser(String email) {
    return new User(USER_ID, email, "password-hash", "User", null, NOW, NOW);
  }
}
