package com.offertrack.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class CookieService {
  public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";
  public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
  private final AuthCookieProperties properties;
  private final Clock clock;

  public CookieService(AuthCookieProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  public void addSessionCookies(HttpServletResponse response, SessionTokens tokens) {
    addCookie(
        response,
        properties.getName(),
        "/",
        tokens.accessToken(),
        remaining(tokens.accessExpiresAt()));
    addCookie(
        response,
        REFRESH_TOKEN_COOKIE_NAME,
        "/auth",
        tokens.refreshToken(),
        remaining(tokens.refreshExpiresAt()));
  }

  public void clearSessionCookies(HttpServletResponse response) {
    addCookie(response, properties.getName(), "/", "", Duration.ZERO);
    addCookie(response, REFRESH_TOKEN_COOKIE_NAME, "/auth", "", Duration.ZERO);
  }

  private Duration remaining(Instant expiry) {
    return Duration.ofSeconds(Math.max(0, Duration.between(clock.instant(), expiry).toSeconds()));
  }

  private void addCookie(
      HttpServletResponse response, String name, String path, String value, Duration maxAge) {
    response.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(name, value)
            .maxAge(maxAge)
            .path(path)
            .httpOnly(true)
            .secure(properties.isSecure())
            .sameSite(properties.getSameSite())
            .build()
            .toString());
  }
}
