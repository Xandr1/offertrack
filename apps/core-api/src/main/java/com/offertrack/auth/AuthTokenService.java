package com.offertrack.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuthTokenService {
  private static final int TOKEN_BYTES = 32;
  private static final int EMAIL_VERIFICATION_TTL_HOURS = 24;

  private final UserAuthTokenRepository userAuthTokenRepository;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  public AuthTokenService(UserAuthTokenRepository userAuthTokenRepository, Clock clock) {
    this.userAuthTokenRepository = userAuthTokenRepository;
    this.clock = clock;
  }

  public String createEmailVerificationToken(UUID userId) {
    String token = generateToken();
    OffsetDateTime now = OffsetDateTime.now(clock);

    userAuthTokenRepository.create(
        userId,
        AuthTokenPurpose.EMAIL_VERIFICATION,
        hashToken(token),
        now.plusHours(EMAIL_VERIFICATION_TTL_HOURS),
        now);

    return token;
  }

  public UUID consumeEmailVerificationToken(String token) {
    String tokenHash = hashToken(token);
    OffsetDateTime now = OffsetDateTime.now(clock);

    return userAuthTokenRepository
        .consumeActiveToken(AuthTokenPurpose.EMAIL_VERIFICATION, tokenHash, now)
        .orElseThrow(InvalidAuthTokenException::new);
  }

  private String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
