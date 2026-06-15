package com.offertrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-13T10:15:30Z"), ZoneOffset.UTC);
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
  void googleLoginRefetchesExistingUserAfterDuplicateInsertRace() {
    User existingUser =
        new User(
            USER_ID,
            "race@example.com",
            null,
            "Race User",
            OffsetDateTime.now(CLOCK),
            OffsetDateTime.now(CLOCK),
            OffsetDateTime.now(CLOCK));

    when(userRepository.findByEmail("race@example.com"))
        .thenReturn(Optional.empty(), Optional.of(existingUser));
    when(userRepository.createVerifiedOAuthUser(
            eq("race@example.com"), eq("Race User"), any(OffsetDateTime.class)))
        .thenThrow(new DuplicateKeyException("race"));
    when(jwtService.generateAccessToken(USER_ID, "race@example.com")).thenReturn("jwt-token");

    AuthService.AuthResult result =
        authService.loginWithGoogle("Race@Example.com", "Race User", true);

    assertThat(result.accessToken()).isEqualTo("jwt-token");
    assertThat(result.response().user().id()).isEqualTo(USER_ID);
    verify(userRepository)
        .createVerifiedOAuthUser(
            eq("race@example.com"), eq("Race User"), any(OffsetDateTime.class));
  }
}
