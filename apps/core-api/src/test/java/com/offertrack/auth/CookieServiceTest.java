package com.offertrack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class CookieServiceTest {
  private CookieService cookieService;

  @BeforeEach
  void setUp() {
    JwtProperties properties = new JwtProperties();
    properties.setAccessTokenTtl(Duration.ofHours(48));
    cookieService = new CookieService(properties);
  }

  @Test
  void addsAccessTokenCookieWithConfiguredLifetimeAndExistingAttributes() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    cookieService.addAccessTokenCookie(response, "access-token");

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
        .contains(
            "access_token=access-token",
            "Max-Age=172800",
            "Path=/",
            "HttpOnly",
            "SameSite=None",
            "Secure");
  }

  @Test
  void clearsAccessTokenCookieImmediatelyWithExistingAttributes() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    cookieService.clearAccessTokenCookie(response);

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
        .contains("access_token=", "Max-Age=0", "Path=/", "HttpOnly", "SameSite=None", "Secure");
  }
}
