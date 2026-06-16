package com.offertrack.auth;

import com.offertrack.auth.dto.AuthResponse;
import com.offertrack.auth.dto.ForgotPasswordRequest;
import com.offertrack.auth.dto.GenericSuccessResponse;
import com.offertrack.auth.dto.LoginRequest;
import com.offertrack.auth.dto.RegisterRequest;
import com.offertrack.auth.dto.RegisterResponse;
import com.offertrack.auth.dto.ResendVerificationRequest;
import com.offertrack.auth.dto.ResetPasswordRequest;
import com.offertrack.auth.dto.VerifyEmailRequest;
import com.offertrack.auth.dto.VerifyEmailResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
  private final AuthService authService;
  private final CookieService cookieService;

  public AuthController(AuthService authService, CookieService cookieService) {
    this.authService = authService;
    this.cookieService = cookieService;
  }

  @GetMapping("/auth/oauth2/google/start")
  public void startGoogleOAuth(HttpServletResponse response) throws IOException {
    response.sendRedirect("/oauth2/authorization/google");
  }

  @PostMapping("/auth/register")
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping("/auth/login")
  public AuthResponse login(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    AuthService.AuthResult result = authService.login(request);
    cookieService.addAccessTokenCookie(response, result.accessToken());

    return result.response();
  }

  @PostMapping("/auth/logout")
  public void logout(HttpServletResponse response) {
    cookieService.clearAccessTokenCookie(response);
  }

  @PostMapping("/auth/email/verify")
  public VerifyEmailResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
    return authService.verifyEmail(request);
  }

  @PostMapping("/auth/email/verification/resend")
  public GenericSuccessResponse resendVerificationEmail(
      @Valid @RequestBody ResendVerificationRequest request) {
    return authService.resendVerificationEmail(request);
  }

  @PostMapping("/auth/password/forgot")
  public GenericSuccessResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    return authService.forgotPassword(request);
  }

  @PostMapping("/auth/password/reset")
  public GenericSuccessResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    return authService.resetPassword(request);
  }
}
