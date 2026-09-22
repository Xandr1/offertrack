package com.offertrack.auth;

import com.offertrack.auth.dto.AuthResponse;
import com.offertrack.auth.dto.CsrfTokenResponse;
import com.offertrack.auth.dto.ForgotPasswordRequest;
import com.offertrack.auth.dto.GenericSuccessResponse;
import com.offertrack.auth.dto.LoginRequest;
import com.offertrack.auth.dto.RegisterRequest;
import com.offertrack.auth.dto.RegisterResponse;
import com.offertrack.auth.dto.ResendVerificationRequest;
import com.offertrack.auth.dto.ResetPasswordRequest;
import com.offertrack.auth.dto.VerifyEmailRequest;
import com.offertrack.auth.dto.VerifyEmailResponse;
import com.offertrack.ratelimit.RateLimitGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
  private final AuthService authService;
  private final CookieService cookieService;
  private final CsrfTokenInvalidationService csrfTokenInvalidationService;
  private final RateLimitGuard rateLimitGuard;
  private final AuthSessionService sessions;
  private final AuthSessionCleanup cleanup;
  private final AuthTokenCleanup tokenCleanup;
  private final JwtAuthenticationFilter jwtFilter;

  public AuthController(
      AuthService authService,
      CookieService cookieService,
      CsrfTokenInvalidationService csrfTokenInvalidationService,
      RateLimitGuard rateLimitGuard,
      AuthSessionService sessions,
      AuthSessionCleanup cleanup,
      AuthTokenCleanup tokenCleanup,
      JwtAuthenticationFilter jwtFilter) {
    this.authService = authService;
    this.cookieService = cookieService;
    this.csrfTokenInvalidationService = csrfTokenInvalidationService;
    this.rateLimitGuard = rateLimitGuard;
    this.sessions = sessions;
    this.cleanup = cleanup;
    this.tokenCleanup = tokenCleanup;
    this.jwtFilter = jwtFilter;
  }

  @GetMapping("/auth/csrf")
  public ResponseEntity<CsrfTokenResponse> csrf(
      @RequestAttribute(name = "_csrf") CsrfToken csrfToken) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName()));
  }

  @GetMapping("/auth/oauth2/google/start")
  public void startGoogleOAuth(HttpServletResponse response) throws IOException {
    response.sendRedirect("/oauth2/authorization/google");
  }

  @PostMapping("/auth/register")
  public RegisterResponse register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
    rateLimitGuard.checkRegistration(request.email(), httpRequest.getRemoteAddr());
    RegisterResponse result = authService.register(request);
    tokenCleanup.afterAuthOperation();
    return result;
  }

  @PostMapping("/auth/login")
  public AuthResponse login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    rateLimitGuard.checkLogin(request.email(), httpRequest.getRemoteAddr());
    AuthService.AuthResult result = authService.login(request);
    tokenCleanup.afterAuthOperation();
    csrfTokenInvalidationService.invalidate(httpRequest, response);
    cleanup.afterAuthOperation();
    cookieService.addSessionCookies(response, result.tokens());

    return result.response();
  }

  @PostMapping("/auth/logout")
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    sessions.logout(
        jwtFilter.extractToken(request).orElse(null),
        JwtAuthenticationFilter.cookie(request, CookieService.REFRESH_TOKEN_COOKIE_NAME)
            .orElse(null));
    cleanup.afterAuthOperation();
    csrfTokenInvalidationService.invalidate(request, response);
    tokenCleanup.afterAuthOperation();
    cookieService.clearSessionCookies(response);
  }

  @PostMapping("/auth/refresh")
  public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
    rateLimitGuard.checkRefresh(request.getRemoteAddr());
    var result =
        sessions.refresh(
            JwtAuthenticationFilter.cookie(request, CookieService.REFRESH_TOKEN_COOKIE_NAME)
                .orElse(null));
    cleanup.afterAuthOperation();
    // refresh() has committed, including replay revocation, before this exception is thrown.
    SessionTokens tokens = result.orElseThrow(AuthenticationRequiredException::new);
    tokenCleanup.afterAuthOperation();
    cookieService.addSessionCookies(response, tokens);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @PostMapping("/auth/logout-all")
  public ResponseEntity<Void> logoutAll(
      @org.springframework.security.core.annotation.AuthenticationPrincipal CurrentUser user,
      HttpServletRequest request,
      HttpServletResponse response) {
    sessions.logoutAll(user);
    tokenCleanup.afterAuthOperation();
    cleanup.afterAuthOperation();
    csrfTokenInvalidationService.invalidate(request, response);
    cookieService.clearSessionCookies(response);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @PostMapping("/auth/email/verify")
  public VerifyEmailResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    VerifyEmailResponse result = authService.verifyEmail(request);
    tokenCleanup.afterAuthOperation();
    return result;
  }

  @PostMapping("/auth/email/verification/resend")
  public GenericSuccessResponse resendVerificationEmail(
      @Valid @RequestBody ResendVerificationRequest request, HttpServletRequest httpRequest) {
    rateLimitGuard.checkVerificationResend(request.email(), httpRequest.getRemoteAddr());
    GenericSuccessResponse result = authService.resendVerificationEmail(request);
    tokenCleanup.afterAuthOperation();
    return result;
  }

  @PostMapping("/auth/password/forgot")
  public GenericSuccessResponse forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
    rateLimitGuard.checkForgotPassword(request.email(), httpRequest.getRemoteAddr());
    GenericSuccessResponse result = authService.forgotPassword(request);
    tokenCleanup.afterAuthOperation();
    return result;
  }

  @PostMapping("/auth/password/reset")
  public GenericSuccessResponse resetPassword(
      @Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
    rateLimitGuard.checkPasswordReset(request.token(), httpRequest.getRemoteAddr());
    GenericSuccessResponse result = authService.resetPassword(request);
    tokenCleanup.afterAuthOperation();
    cleanup.afterAuthOperation();
    return result;
  }
}
