package com.offertrack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

class OAuth2AuthorizationRequestCookieClearingHandlerTest {
  private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository =
      new CookieOAuth2AuthorizationRequestRepository("test-oauth-cookie-secret");

  @Test
  void failureHandlerClearsAuthorizationRequestCookieBeforeDelegating() throws Exception {
    AtomicBoolean cookieClearedBeforeDelegate = new AtomicBoolean(false);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationFailureHandler delegate =
        (request, delegateResponse, exception) -> {
          cookieClearedBeforeDelegate.set(hasClearingCookie(response));
          delegateResponse.sendRedirect("/login?oauthError=google");
        };
    OAuth2AuthorizationRequestCookieClearingFailureHandler handler =
        new OAuth2AuthorizationRequestCookieClearingFailureHandler(
            authorizationRequestRepository, delegate);

    handler.onAuthenticationFailure(
        new MockHttpServletRequest(), response, new BadCredentialsException("failed"));

    assertThat(cookieClearedBeforeDelegate).isTrue();
    assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauthError=google");
    assertThat(hasClearingCookie(response)).isTrue();
  }

  @Test
  void failureHandlerRedirectsToFrontendLoginByDefault() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    OAuth2AuthorizationRequestCookieClearingFailureHandler handler =
        new OAuth2AuthorizationRequestCookieClearingFailureHandler(
            authorizationRequestRepository, "http://localhost:3000/");

    handler.onAuthenticationFailure(
        new MockHttpServletRequest(), response, new BadCredentialsException("failed"));

    assertThat(response.getRedirectedUrl())
        .isEqualTo("http://localhost:3000/login?oauthError=google");
    assertThat(hasClearingCookie(response)).isTrue();
  }

  private static boolean hasClearingCookie(MockHttpServletResponse response) {
    String cookieName =
        CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME;

    return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
        .anyMatch(
            header ->
                header.contains(cookieName + "=")
                    && header.contains("Max-Age=0")
                    && header.contains("Path=/")
                    && header.contains("HttpOnly")
                    && header.contains("SameSite=Lax")
                    && header.contains("Secure"));
  }
}
