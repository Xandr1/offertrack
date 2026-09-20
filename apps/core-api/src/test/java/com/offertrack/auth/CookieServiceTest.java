package com.offertrack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class CookieServiceTest {
  private final Instant now = Instant.parse("2026-09-20T12:00:00Z");

  @Test
  void setsAndClearsHostOnlyCookiesWithExactProtectedAttributes() {
    AuthCookieProperties properties = new AuthCookieProperties();
    properties.setSecure(true);
    CookieService cookies = new CookieService(properties, Clock.fixed(now, ZoneOffset.UTC));
    var set = new MockHttpServletResponse();
    cookies.addSessionCookies(
        set, new SessionTokens("access", "refresh", now.plusSeconds(900), now.plusSeconds(604800)));
    assertThat(set.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2);
    assertThat(set.getHeaders(HttpHeaders.SET_COOKIE).get(0))
        .contains(
            "access_token=access;",
            "Path=/;",
            "Max-Age=900;",
            "Secure;",
            "HttpOnly;",
            "SameSite=Lax")
        .doesNotContain("Domain=");
    assertThat(set.getHeaders(HttpHeaders.SET_COOKIE).get(1))
        .contains(
            "refresh_token=refresh;",
            "Path=/auth;",
            "Max-Age=604800;",
            "Secure;",
            "HttpOnly;",
            "SameSite=Lax")
        .doesNotContain("Domain=");
    var clear = new MockHttpServletResponse();
    cookies.clearSessionCookies(clear);
    assertThat(clear.getHeaders(HttpHeaders.SET_COOKIE))
        .hasSize(2)
        .allSatisfy(
            value ->
                assertThat(value)
                    .contains("Max-Age=0;", "Secure;", "HttpOnly;", "SameSite=Lax")
                    .doesNotContain("Domain="));
    assertThat(clear.getHeaders(HttpHeaders.SET_COOKIE).get(1)).contains("Path=/auth;");
  }

  @Test
  void localCookiesRetainHttpOnlyLaxAndClipRemainingLifetime() {
    CookieService cookies =
        new CookieService(new AuthCookieProperties(), Clock.fixed(now, ZoneOffset.UTC));
    var response = new MockHttpServletResponse();
    cookies.addSessionCookies(
        response, new SessionTokens("access", "refresh", now.plusSeconds(20), now.plusSeconds(30)));
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE).getFirst())
        .contains("HttpOnly;", "SameSite=Lax", "Max-Age=20;")
        .doesNotContain("Secure", "Domain=");
  }
}
