package com.offertrack.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

@Service
public class CookieService {
  public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

  private final JwtProperties properties;

  public CookieService(JwtProperties properties) {
    this.properties = properties;
  }

  public void addAccessTokenCookie(HttpServletResponse response, String accessToken) {
    String cookie =
        ACCESS_TOKEN_COOKIE_NAME
            + "="
            + accessToken
            + "; Max-Age="
            + properties.getAccessTokenTtl().toSeconds()
            + "; Path=/"
            + "; HttpOnly"
            + "; SameSite=None"
            + "; Secure";

    response.addHeader("Set-Cookie", cookie);
  }

  public void clearAccessTokenCookie(HttpServletResponse response) {
    String cookie =
        ACCESS_TOKEN_COOKIE_NAME
            + "="
            + "; Max-Age=0"
            + "; Path=/"
            + "; HttpOnly"
            + "; SameSite=None"
            + "; Secure";

    response.addHeader("Set-Cookie", cookie);
  }
}
