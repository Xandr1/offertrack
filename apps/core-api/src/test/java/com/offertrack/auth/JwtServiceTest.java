package com.offertrack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtServiceTest {
  private static final String SECRET =
      "test-secret-test-secret-test-secret-test-secret-test-secret";

  @Test
  void generatesAccessTokenThatExpiresAfterFortyEightHours() {
    JwtProperties properties = properties(Duration.ofHours(48));
    JwtService service = new JwtService(properties);

    Instant beforeIssue = Instant.now();
    Claims claims = claims(service.generateAccessToken(UUID.randomUUID(), "user@example.com"));
    Instant afterIssue = Instant.now();

    assertThat(claims.getExpiration().toInstant())
        .isBetween(
            beforeIssue.plus(Duration.ofHours(48)).minusSeconds(1),
            afterIssue.plus(Duration.ofHours(48)).plusSeconds(1));
  }

  @Test
  void jwtAndCookieUseTheSameConfiguredDuration() {
    Duration configuredTtl = Duration.ofMinutes(90);
    JwtProperties properties = properties(configuredTtl);
    JwtService jwtService = new JwtService(properties);
    CookieService cookieService = new CookieService(properties, new AuthCookieProperties());
    MockHttpServletResponse response = new MockHttpServletResponse();

    Claims claims = claims(jwtService.generateAccessToken(UUID.randomUUID(), "user@example.com"));
    cookieService.addAccessTokenCookie(response, "token");

    assertThat(
            Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
        .isEqualTo(configuredTtl);
    assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=5400");
  }

  private static Claims claims(String token) {
    return Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  private static JwtProperties properties(Duration ttl) {
    JwtProperties properties = new JwtProperties();
    properties.setSecret(SECRET);
    properties.setAccessTokenTtl(ttl);
    return properties;
  }
}
