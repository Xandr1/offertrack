package com.offertrack.auth;

import com.offertrack.auth.dto.AuthResponse;
import com.offertrack.auth.dto.ForgotPasswordRequest;
import com.offertrack.auth.dto.GenericSuccessResponse;
import com.offertrack.auth.dto.LoginRequest;
import com.offertrack.auth.dto.RegisterRequest;
import com.offertrack.auth.dto.RegisterResponse;
import com.offertrack.auth.dto.ResendVerificationRequest;
import com.offertrack.auth.dto.ResetPasswordRequest;
import com.offertrack.auth.dto.VerifyEmailRequest;
import com.offertrack.auth.dto.VerifyEmailResponse;
import com.offertrack.users.User;
import com.offertrack.users.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final int MAX_GOOGLE_DISPLAY_NAME_LENGTH = 255;

  private final UserRepository userRepository;
  private final PasswordService passwordService;
  private final JwtService jwtService;
  private final AuthTokenService authTokenService;
  private final AuthEmailService authEmailService;
  private final Clock clock;

  public AuthService(
      UserRepository userRepository,
      PasswordService passwordService,
      JwtService jwtService,
      AuthTokenService authTokenService,
      AuthEmailService authEmailService,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordService = passwordService;
    this.jwtService = jwtService;
    this.authTokenService = authTokenService;
    this.authEmailService = authEmailService;
    this.clock = clock;
  }

  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();
    String passwordHash = passwordService.hash(request.password());

    try {
      User user = userRepository.createUser(normalizedEmail, passwordHash, request.name());
      sendEmailVerification(user);

      return new RegisterResponse(true);
    } catch (DuplicateKeyException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this email already exists");
    }
  }

  public AuthResult login(LoginRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();

    User user =
        userRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"));

    String passwordHash = user.passwordHash();
    if (passwordHash == null || !passwordService.matches(request.password(), passwordHash)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    if (user.emailVerifiedAt() == null) {
      throw new EmailNotVerifiedException();
    }

    return buildAuthResult(user);
  }

  @Transactional
  public AuthResult loginWithGoogle(String email, String name, boolean emailVerified) {
    if (!StringUtils.hasText(email)) {
      throw new IllegalArgumentException("Google email must not be blank");
    }

    if (!emailVerified) {
      throw new IllegalArgumentException("Google email must be verified");
    }

    String normalizedEmail = email.trim().toLowerCase();
    String normalizedName = normalizeGoogleDisplayName(name);
    OffsetDateTime verifiedAt = OffsetDateTime.now(clock);

    User user =
        userRepository
            .insertVerifiedOAuthUserIfAbsent(normalizedEmail, normalizedName, verifiedAt)
            .orElseGet(() -> findAndVerifyExistingGoogleUser(normalizedEmail, verifiedAt));

    return buildAuthResult(user);
  }

  @Transactional
  public VerifyEmailResponse verifyEmail(VerifyEmailRequest request) {
    UUID userId = authTokenService.consumeEmailVerificationToken(request.token());
    userRepository.markEmailVerified(userId, OffsetDateTime.now(clock));

    return new VerifyEmailResponse(true);
  }

  @Transactional
  public GenericSuccessResponse resendVerificationEmail(ResendVerificationRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();

    userRepository
        .findByEmail(normalizedEmail)
        .filter(user -> user.emailVerifiedAt() == null)
        .ifPresent(this::sendEmailVerification);

    return new GenericSuccessResponse(true);
  }

  @Transactional
  public GenericSuccessResponse forgotPassword(ForgotPasswordRequest request) {
    String normalizedEmail = request.email().trim().toLowerCase();

    userRepository.findByEmail(normalizedEmail).ifPresent(this::sendPasswordResetEmail);

    return new GenericSuccessResponse(true);
  }

  @Transactional
  public GenericSuccessResponse resetPassword(ResetPasswordRequest request) {
    UUID userId = authTokenService.consumePasswordResetToken(request.token());
    String passwordHash = passwordService.hash(request.newPassword());

    userRepository.updatePasswordHash(userId, passwordHash, OffsetDateTime.now(clock));
    authTokenService.consumeActivePasswordResetTokens(userId);

    return new GenericSuccessResponse(true);
  }

  private AuthResult buildAuthResult(User user) {
    String accessToken = jwtService.generateAccessToken(user.id(), user.email());

    AuthResponse response =
        new AuthResponse(new AuthResponse.UserSummary(user.id(), user.email(), user.name()));

    return new AuthResult(accessToken, response);
  }

  private User findAndVerifyExistingGoogleUser(String normalizedEmail, OffsetDateTime verifiedAt) {
    User user =
        userRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(
                () -> new IllegalStateException("Google OAuth user was not created or found"));

    return verifyExistingGoogleUserIfNeeded(user, verifiedAt);
  }

  private User verifyExistingGoogleUserIfNeeded(User user, OffsetDateTime verifiedAt) {
    if (user.emailVerifiedAt() != null) {
      return user;
    }

    userRepository.markEmailVerified(user.id(), verifiedAt);
    return userRepository.findByEmail(user.email()).orElse(user);
  }

  private String normalizeGoogleDisplayName(String name) {
    if (!StringUtils.hasText(name)) {
      return null;
    }

    String normalizedName = name.trim();
    if (normalizedName.length() <= MAX_GOOGLE_DISPLAY_NAME_LENGTH) {
      return normalizedName;
    }

    return normalizedName.substring(0, MAX_GOOGLE_DISPLAY_NAME_LENGTH);
  }

  private void sendEmailVerification(User user) {
    String token = authTokenService.createEmailVerificationToken(user.id());
    authEmailService.sendVerificationEmail(user, token);
  }

  private void sendPasswordResetEmail(User user) {
    String token = authTokenService.createPasswordResetToken(user.id());

    try {
      authEmailService.sendPasswordResetEmail(user, token);
    } catch (RuntimeException exception) {
      log.warn("Could not send password reset email for user {}", user.id(), exception);
    }
  }

  public record AuthResult(String accessToken, AuthResponse response) {}
}
