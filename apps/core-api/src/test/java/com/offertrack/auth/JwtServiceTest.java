package com.offertrack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  private static final String SECRET =
      "test-secret-test-secret-test-secret-test-secret-test-secret";
  private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  private JwtService service() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret(SECRET);
    return new JwtService(properties, clock);
  }

  @Test
  void issuesOnlyFiveClaimsForFifteenMinutes() {
    UUID user = UUID.randomUUID(), session = UUID.randomUUID();
    var issued = service().generateAccessToken(user, session, NOW.plusSeconds(86400));
    Claims claims =
        Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .clock(() -> Date.from(NOW))
            .build()
            .parseSignedClaims(issued.token())
            .getPayload();
    assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "sid", "iat", "exp", "jti");
    assertThat(claims.getSubject()).isEqualTo(user.toString());
    assertThat(claims.get("sid")).isEqualTo(session.toString());
    assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(900));
    assertThat(service().verify(issued.token()))
        .hasValueSatisfying(value -> assertThat(value.sessionId()).isEqualTo(session));
  }

  @Test
  void capsAccessAtSessionExpiry() {
    var issued =
        service().generateAccessToken(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(45));
    assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(45));
  }

  @Test
  void rejectsLegacyClaimsAndMalformedIdentifiers() {
    String legacy =
        Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .claim("email", "test@example.com")
            .issuedAt(Date.from(NOW))
            .expiration(Date.from(NOW.plusSeconds(172800)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    assertThat(service().verify(legacy)).isEmpty();
    assertThat(service().verify("invalid")).isEmpty();
    assertThat(service().verify(null)).isEmpty();
    String malformed =
        Jwts.builder()
            .subject("not-a-uuid")
            .claim("sid", UUID.randomUUID().toString())
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(NOW))
            .expiration(Date.from(NOW.plusSeconds(900)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    assertThat(service().verify(malformed)).isEmpty();
  }
}
