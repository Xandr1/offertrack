package com.offertrack.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.util.StringUtils;

public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {
  private static final Logger log = LoggerFactory.getLogger(GoogleOAuth2SuccessHandler.class);
  private static final String GOOGLE_REGISTRATION_ID = "google";

  private final AuthService authService;
  private final CookieService cookieService;
  private final CsrfTokenInvalidationService csrfTokenInvalidationService;
  private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
  private final String dashboardRedirectUrl;
  private final String failureRedirectUrl;
  private final AuthSessionCleanup cleanup;
  private final AuthTokenCleanup tokenCleanup;

  public GoogleOAuth2SuccessHandler(
      AuthService authService,
      CookieService cookieService,
      CsrfTokenInvalidationService csrfTokenInvalidationService,
      CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository,
      String appWebUrl,
      AuthSessionCleanup cleanup,
      AuthTokenCleanup tokenCleanup) {
    this.authService = authService;
    this.cookieService = cookieService;
    this.csrfTokenInvalidationService = csrfTokenInvalidationService;
    this.authorizationRequestRepository = authorizationRequestRepository;
    this.cleanup = cleanup;
    this.tokenCleanup = tokenCleanup;

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
      OidcUser oidcUser = requireGoogleOidcUser(authentication);
      String name = displayName(oidcUser);

      result =
          authService.loginWithGoogle(
              new GoogleIdentity(
                  oidcUser.getSubject(),
                  oidcUser.getEmail(),
                  name,
                  Boolean.TRUE.equals(oidcUser.getEmailVerified())));
    } catch (RuntimeException exception) {
      log.warn(
          "oauth_login_failed operation=google_oauth_login error_type={}",
          exception.getClass().getSimpleName());
      redirectToFailure(response);
      return;
    }

    csrfTokenInvalidationService.invalidate(request, response);
    cleanup.afterAuthOperation();
    tokenCleanup.afterAuthOperation();
    cookieService.addSessionCookies(response, result.tokens());
    authorizationRequestRepository.clearAuthorizationRequestCookie(response);
    response.sendRedirect(dashboardRedirectUrl);
  }

  private OidcUser requireGoogleOidcUser(Authentication authentication) {
    if (!(authentication instanceof OAuth2AuthenticationToken oauth2Authentication)) {
      throw new IllegalArgumentException("Google OAuth authentication token is required");
    }

    if (!GOOGLE_REGISTRATION_ID.equals(oauth2Authentication.getAuthorizedClientRegistrationId())) {
      throw new IllegalArgumentException("OAuth provider must be Google");
    }

    if (!(oauth2Authentication.getPrincipal() instanceof OidcUser oidcUser)) {
      throw new IllegalArgumentException("Google OAuth principal must be an OIDC user");
    }

    return oidcUser;
  }

  private String displayName(OidcUser oidcUser) {
    if (StringUtils.hasText(oidcUser.getFullName())) {
      return oidcUser.getFullName();
    }

    return null;
  }

  private void redirectToFailure(HttpServletResponse response) throws IOException {
    authorizationRequestRepository.clearAuthorizationRequestCookie(response);
    response.sendRedirect(failureRedirectUrl);
  }
}
