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
    AuthCookieProperties cookieProperties = new AuthCookieProperties();
    cookieService = new CookieService(properties, cookieProperties);
  }

  @Test
  void addsAccessTokenCookieWithConfiguredLifetimeAndSafeLocalAttributes() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    cookieService.addAccessTokenCookie(response, "access-token");

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
        .contains(
            "access_token=access-token", "Max-Age=172800", "Path=/", "HttpOnly", "SameSite=Lax")
        .doesNotContain("Secure");
  }

  @Test
  void clearsAccessTokenCookieImmediatelyWithMatchingAttributes() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    cookieService.clearAccessTokenCookie(response);

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
        .contains("access_token=", "Max-Age=0", "Path=/", "HttpOnly", "SameSite=Lax")
        .doesNotContain("Secure");
  }

  @Test
  void appliesCustomNameDomainAndProtectedAttributesToSetAndClear() {
    JwtProperties jwtProperties = new JwtProperties();
    jwtProperties.setAccessTokenTtl(Duration.ofMinutes(5));
    AuthCookieProperties cookieProperties = new AuthCookieProperties();
    cookieProperties.setName("offertrack_session");
    cookieProperties.setDomain(".example.com");
    cookieProperties.setSecure(true);
    cookieProperties.setSameSite("None");
    CookieService configuredService = new CookieService(jwtProperties, cookieProperties);
    MockHttpServletResponse setResponse = new MockHttpServletResponse();
    MockHttpServletResponse clearResponse = new MockHttpServletResponse();

    configuredService.addAccessTokenCookie(setResponse, "token");
    configuredService.clearAccessTokenCookie(clearResponse);

    assertThat(setResponse.getHeader(HttpHeaders.SET_COOKIE))
        .contains(
            "offertrack_session=token",
            "Max-Age=300",
            "Domain=.example.com",
            "Path=/",
            "Secure",
            "HttpOnly",
            "SameSite=None");
    assertThat(clearResponse.getHeader(HttpHeaders.SET_COOKIE))
        .contains(
            "offertrack_session=",
            "Max-Age=0",
            "Domain=.example.com",
            "Path=/",
            "Secure",
            "HttpOnly",
            "SameSite=None");
  }
}
