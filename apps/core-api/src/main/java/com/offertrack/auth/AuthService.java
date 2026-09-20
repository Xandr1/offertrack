package com.offertrack.auth;

import com.offertrack.auth.dto.*;
import com.offertrack.users.User;
import com.offertrack.users.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
public class AuthService {
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private final UserRepository userRepository;
  private final PasswordService passwordService;
  private final AuthSessionService sessions;
  private final UserIdentityRepository identities;
  private final AuthTokenService authTokenService;
  private final AuthEmailService authEmailService;
  private final Clock clock;

  public AuthService(
      UserRepository userRepository,
      PasswordService passwordService,
      AuthSessionService sessions,
      UserIdentityRepository identities,
      AuthTokenService authTokenService,
      AuthEmailService authEmailService,
      Clock clock) {
    this.userRepository = userRepository;
    this.passwordService = passwordService;
    this.sessions = sessions;
    this.identities = identities;
    this.authTokenService = authTokenService;
    this.authEmailService = authEmailService;
    this.clock = clock;
  }

  public RegisterResponse register(RegisterRequest request) {
    User user =
        userRepository.createUser(
            normalizeEmail(request.email()),
            passwordService.hash(request.password()),
            request.name());
    if (user == null)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this email already exists");
    sendEmailVerification(user);
    return new RegisterResponse(true);
  }

  public AuthResult login(LoginRequest request) {
    User discovered =
        userRepository
            .findByEmail(normalizeEmail(request.email()))
            .orElseThrow(AuthService::invalidPassword);
    String hash = discovered.passwordHash();
    if (hash == null || !passwordService.matches(request.password(), hash)) throw invalidPassword();
    User user = userRepository.lockById(discovered.id()).orElseThrow(AuthService::invalidPassword);
    if (!Objects.equals(hash, user.passwordHash())) throw invalidPassword();
    if (user.emailVerifiedAt() == null) throw new EmailNotVerifiedException();
    return buildAuthResult(user);
  }

  public AuthResult loginWithGoogle(GoogleIdentity identity) {
    identities.lockGoogleSubject(identity.subject());
    UUID linked = identities.findGoogleUser(identity.subject()).orElse(null);
    if (linked != null) {
      return buildAuthResult(
          userRepository.lockById(linked).orElseThrow(AuthenticationRequiredException::new));
    }
    if (!identity.emailVerified()
        || !StringUtils.hasText(identity.email())
        || identity.email().length() > 255) throw new AuthenticationRequiredException();
    String email = normalizeEmail(identity.email());
    OffsetDateTime now = OffsetDateTime.now(clock);
    userRepository.insertVerifiedOAuthUserIfAbsent(
        email, normalizeGoogleDisplayName(identity.name()), now);
    User user = userRepository.lockByEmail(email).orElseThrow(AuthenticationRequiredException::new);
    if (identities.hasGoogleIdentity(user.id())) throw new AuthenticationRequiredException();
    now = OffsetDateTime.now(clock);
    if (user.emailVerifiedAt() == null) {
      user = userRepository.claimUnverified(user.id(), now);
      sessions.revokeAllLocked(user.id(), SessionRevocationReason.OAUTH_ACCOUNT_CLAIM);
      authTokenService.consumeAllForUser(user.id());
    }
    if (!identities.linkGoogle(user.id(), identity.subject(), now))
      throw new AuthenticationRequiredException();
    return buildAuthResult(user);
  }

  public VerifyEmailResponse verifyEmail(VerifyEmailRequest request) {
    UUID userId = authTokenService.findUser(request.token(), AuthTokenPurpose.EMAIL_VERIFICATION);
    userRepository.lockById(userId).orElseThrow(InvalidAuthTokenException::new);
    authTokenService.consumeEmailVerificationToken(request.token());
    userRepository.markEmailVerified(userId, OffsetDateTime.now(clock));
    return new VerifyEmailResponse(true);
  }

  public GenericSuccessResponse resendVerificationEmail(ResendVerificationRequest request) {
    userRepository
        .lockByEmail(normalizeEmail(request.email()))
        .filter(user -> user.emailVerifiedAt() == null)
        .ifPresent(this::sendEmailVerification);
    return new GenericSuccessResponse(true);
  }

  public GenericSuccessResponse forgotPassword(ForgotPasswordRequest request) {
    userRepository
        .lockByEmail(normalizeEmail(request.email()))
        .ifPresent(this::sendPasswordResetEmail);
    return new GenericSuccessResponse(true);
  }

  public GenericSuccessResponse resetPassword(ResetPasswordRequest request) {
    UUID userId = authTokenService.findUser(request.token(), AuthTokenPurpose.PASSWORD_RESET);
    String passwordHash = passwordService.hash(request.newPassword());
    userRepository.lockById(userId).orElseThrow(InvalidAuthTokenException::new);
    // Session locks precede recovery-token writes, consistently with Google claiming.
    sessions.revokeAllLocked(userId, SessionRevocationReason.PASSWORD_RESET);
    authTokenService.consumePasswordResetToken(request.token());
    userRepository.updatePasswordHash(userId, passwordHash, OffsetDateTime.now(clock));
    authTokenService.consumeActivePasswordResetTokens(userId);
    return new GenericSuccessResponse(true);
  }

  private AuthResult buildAuthResult(User user) {
    SessionTokens tokens = sessions.create(user.id());
    AuthResponse response =
        new AuthResponse(new AuthResponse.UserSummary(user.id(), user.email(), user.name()));
    return new AuthResult(tokens, response);
  }

  private static ResponseStatusException invalidPassword() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeGoogleDisplayName(String name) {
    if (!StringUtils.hasText(name)) return null;
    String normalized = name.trim();
    return normalized.substring(0, Math.min(normalized.length(), 255));
  }

  private void sendEmailVerification(User user) {
    String token = authTokenService.createEmailVerificationToken(user.id());
    try {
      authEmailService.sendVerificationEmail(user, token);
    } catch (RuntimeException exception) {
      log.warn(
          "email_verification_send_failed error_type={}", exception.getClass().getSimpleName());
      throw new AuthServiceUnavailableException();
    }
  }

  private void sendPasswordResetEmail(User user) {
    String token = authTokenService.createPasswordResetToken(user.id());
    try {
      authEmailService.sendPasswordResetEmail(user, token);
    } catch (RuntimeException exception) {
      log.warn("password_reset_send_failed error_type={}", exception.getClass().getSimpleName());
    }
  }

  public record AuthResult(SessionTokens tokens, AuthResponse response) {
    public String accessToken() {
      return tokens.accessToken();
    }

    @Override
    public String toString() {
      return "AuthResult[redacted]";
    }
  }
}
