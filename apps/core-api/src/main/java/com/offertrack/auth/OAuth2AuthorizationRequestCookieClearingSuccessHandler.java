package com.offertrack.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

public class OAuth2AuthorizationRequestCookieClearingSuccessHandler
    implements AuthenticationSuccessHandler {
  private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
  private final AuthenticationSuccessHandler delegate;

  public OAuth2AuthorizationRequestCookieClearingSuccessHandler(
      CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository) {
    this(authorizationRequestRepository, new SimpleUrlAuthenticationSuccessHandler("/"));
  }

  OAuth2AuthorizationRequestCookieClearingSuccessHandler(
      CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository,
      AuthenticationSuccessHandler delegate) {
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.delegate = delegate;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    authorizationRequestRepository.clearAuthorizationRequestCookie(response);
    delegate.onAuthenticationSuccess(request, response, authentication);
  }
}
