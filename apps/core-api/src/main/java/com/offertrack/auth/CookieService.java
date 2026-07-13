package com.offertrack.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CookieService {
  public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

  private final JwtProperties jwtProperties;
  private final AuthCookieProperties cookieProperties;

  public CookieService(JwtProperties jwtProperties, AuthCookieProperties cookieProperties) {
    this.jwtProperties = jwtProperties;
    this.cookieProperties = cookieProperties;
  }

  public void addAccessTokenCookie(HttpServletResponse response, String accessToken) {
    addCookie(response, accessToken, jwtProperties.getAccessTokenTtl());
  }

  public void clearAccessTokenCookie(HttpServletResponse response) {
    addCookie(response, "", Duration.ZERO);
  }

  private void addCookie(HttpServletResponse response, String value, Duration maxAge) {
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(cookieProperties.getName(), value)
            .maxAge(maxAge)
            .path(cookieProperties.getPath())
            .httpOnly(true)
            .secure(cookieProperties.isSecure())
            .sameSite(cookieProperties.getSameSite());

    if (StringUtils.hasText(cookieProperties.getDomain())) {
      builder.domain(cookieProperties.getDomain().trim());
    }

    response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
  }
}
