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

  public AuthController(
      AuthService authService,
      CookieService cookieService,
      CsrfTokenInvalidationService csrfTokenInvalidationService,
      RateLimitGuard rateLimitGuard) {
    this.authService = authService;
    this.cookieService = cookieService;
    this.csrfTokenInvalidationService = csrfTokenInvalidationService;
    this.rateLimitGuard = rateLimitGuard;
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
    return authService.register(request);
  }

  @PostMapping("/auth/login")
  public AuthResponse login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse response) {
    rateLimitGuard.checkLogin(request.email(), httpRequest.getRemoteAddr());
    AuthService.AuthResult result = authService.login(request);
    csrfTokenInvalidationService.invalidate(httpRequest, response);
    cookieService.addAccessTokenCookie(response, result.accessToken());

    return result.response();
  }

  @PostMapping("/auth/logout")
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    csrfTokenInvalidationService.invalidate(request, response);
    cookieService.clearAccessTokenCookie(response);
  }

  @PostMapping("/auth/email/verify")
  public VerifyEmailResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    return authService.verifyEmail(request);
  }

  @PostMapping("/auth/email/verification/resend")
  public GenericSuccessResponse resendVerificationEmail(
      @Valid @RequestBody ResendVerificationRequest request, HttpServletRequest httpRequest) {
    rateLimitGuard.checkVerificationResend(request.email(), httpRequest.getRemoteAddr());
    return authService.resendVerificationEmail(request);
  }

  @PostMapping("/auth/password/forgot")
  public GenericSuccessResponse forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
    rateLimitGuard.checkForgotPassword(request.email(), httpRequest.getRemoteAddr());
    return authService.forgotPassword(request);
  }

  @PostMapping("/auth/password/reset")
  public GenericSuccessResponse resetPassword(
      @Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
    rateLimitGuard.checkPasswordReset(request.token(), httpRequest.getRemoteAddr());
    return authService.resetPassword(request);
  }
}
