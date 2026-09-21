package com.offertrack.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey signingKey;
  private final JwtProperties properties;
  private final Clock clock;

  public JwtService(JwtProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
  }

  public IssuedAccess generateAccessToken(UUID userId, UUID sessionId, Instant sessionExpiry) {
    Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    Instant expiresAt = now.plus(properties.getAccessTokenTtl());
    if (sessionExpiry.isBefore(expiresAt))
      expiresAt = sessionExpiry.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    if (!expiresAt.isAfter(now)) throw new AuthenticationRequiredException();
    return new IssuedAccess(
        Jwts.builder()
            .subject(userId.toString())
            .claim("sid", sessionId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .id(UUID.randomUUID().toString())
            .signWith(signingKey)
            .compact(),
        expiresAt);
  }

  public Optional<AccessClaims> verify(String token) {
    if (token == null || token.length() > 2048) return Optional.empty();
    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(signingKey)
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(token)
              .getPayload();
      if (!claims.keySet().equals(Set.of("sub", "sid", "iat", "exp", "jti")))
        return Optional.empty();
      UUID userId = uuid(claims.getSubject());
      UUID sessionId = uuid(claims.get("sid", String.class));
      uuid(claims.getId());
      Instant issuedAt = claims.getIssuedAt().toInstant();
      Instant expiresAt = claims.getExpiration().toInstant();
      if (issuedAt.isAfter(clock.instant())
          || !expiresAt.isAfter(issuedAt)
          || expiresAt.isAfter(issuedAt.plus(properties.getAccessTokenTtl())))
        return Optional.empty();
      return Optional.of(new AccessClaims(userId, sessionId, issuedAt, expiresAt));
    } catch (RuntimeException exception) {
      return Optional.empty();
    }
  }

  private static UUID uuid(String value) {
    UUID result = UUID.fromString(value);
    if (!result.toString().equals(value)) throw new IllegalArgumentException("Invalid identifier");
    return result;
  }

  public record AccessClaims(UUID userId, UUID sessionId, Instant issuedAt, Instant expiresAt) {
    @Override
    public String toString() {
      return "AccessClaims[redacted]";
    }
  }

  public record IssuedAccess(String token, Instant expiresAt) {
    @Override
    public String toString() {
      return "IssuedAccess[redacted]";
    }
  }
}
