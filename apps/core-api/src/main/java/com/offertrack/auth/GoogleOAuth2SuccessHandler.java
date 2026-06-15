package com.offertrack.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.util.StringUtils;

public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {
  private final AuthService authService;
  private final CookieService cookieService;
  private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
  private final String dashboardRedirectUrl;
  private final String failureRedirectUrl;

  public GoogleOAuth2SuccessHandler(
      AuthService authService,
      CookieService cookieService,
      CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository,
      String appWebUrl) {
    this.authService = authService;
    this.cookieService = cookieService;
    this.authorizationRequestRepository = authorizationRequestRepository;

    String normalizedWebUrl = appWebUrl.replaceAll("/+$", "");
    this.dashboardRedirectUrl = normalizedWebUrl + "/dashboard";
    this.failureRedirectUrl = normalizedWebUrl + "/login?oauthError=google";
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    AuthService.AuthResult result;

    try {
      OidcUser oidcUser = requireOidcUser(authentication);
      String email = requireEmail(oidcUser);
      boolean emailVerified = requireVerifiedEmail(oidcUser);
      String name = displayName(oidcUser);

      result = authService.loginWithGoogle(email, name, emailVerified);
    } catch (RuntimeException exception) {
      redirectToFailure(response);
      return;
    }

    cookieService.addAccessTokenCookie(response, result.accessToken());
    authorizationRequestRepository.clearAuthorizationRequestCookie(response);
    response.sendRedirect(dashboardRedirectUrl);
  }

  private OidcUser requireOidcUser(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
      throw new IllegalArgumentException("Google OAuth principal must be an OIDC user");
    }

    return oidcUser;
  }

  private String requireEmail(OidcUser oidcUser) {
    String email = oidcUser.getEmail();

    if (!StringUtils.hasText(email)) {
      throw new IllegalArgumentException("Google email must not be blank");
    }

    return email;
  }

  private boolean requireVerifiedEmail(OidcUser oidcUser) {
    Boolean emailVerified = oidcUser.getEmailVerified();

    if (!Boolean.TRUE.equals(emailVerified)) {
      throw new IllegalArgumentException("Google email must be verified");
    }

    return true;
  }

  private String displayName(OidcUser oidcUser) {
    if (StringUtils.hasText(oidcUser.getFullName())) {
      return oidcUser.getFullName();
    }

    return StringUtils.hasText(oidcUser.getName()) ? oidcUser.getName() : null;
  }

  private void redirectToFailure(HttpServletResponse response) throws IOException {
    authorizationRequestRepository.clearAuthorizationRequestCookie(response);
    response.sendRedirect(failureRedirectUrl);
  }
}
