package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.offertrack.auth.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BrowserCsrfRequestMatcherTest {
  private final BrowserCsrfRequestMatcher matcher =
      new BrowserCsrfRequestMatcher(new AuthCookieProperties());

  @Test
  void protectsPublicAuthMutationsWithServletPathParameters() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/auth/login;transport=browser");

    assertThat(matcher.matches(request)).isTrue();
  }

  @Test
  void protectsPublicAuthMutationsWithEncodedPathCharacters() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/log%69n");

    assertThat(matcher.matches(request)).isTrue();
  }

  @Test
  void cookiePresenceStillControlsOtherUnsafeRoutes() {
    MockHttpServletRequest bearerOnlyRequest = new MockHttpServletRequest("PATCH", "/api/settings");
    MockHttpServletRequest cookieRequest = new MockHttpServletRequest("PATCH", "/api/settings");
    cookieRequest.setCookies(new Cookie("access_token", "even-an-invalid-token"));

    assertThat(matcher.matches(bearerOnlyRequest)).isFalse();
    assertThat(matcher.matches(cookieRequest)).isTrue();
  }
}
