package com.offertrack.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

public class OAuth2AuthorizationRequestCookieClearingFailureHandler
    implements AuthenticationFailureHandler {
  private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
  private final AuthenticationFailureHandler delegate;

  public OAuth2AuthorizationRequestCookieClearingFailureHandler(
      CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository, String appWebUrl) {
    this(authorizationRequestRepository, defaultFailureHandler(appWebUrl));
  }

  OAuth2AuthorizationRequestCookieClearingFailureHandler(
      CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository,
      AuthenticationFailureHandler delegate) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.delegate = delegate;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    authorizationRequestRepository.clearAuthorizationRequestCookie(response);
    delegate.onAuthenticationFailure(request, response, exception);
  }

  private static SimpleUrlAuthenticationFailureHandler defaultFailureHandler(String appWebUrl) {
    SimpleUrlAuthenticationFailureHandler failureHandler =
        new SimpleUrlAuthenticationFailureHandler(
            appWebUrl.replaceAll("/+$", "") + "/login?oauthError=google");
    failureHandler.setAllowSessionCreation(false);
    return failureHandler;
  }
}
