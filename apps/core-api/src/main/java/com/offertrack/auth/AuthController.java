package com.offertrack.auth;

import com.offertrack.auth.dto.AuthResponse;
import com.offertrack.auth.dto.LoginRequest;
import com.offertrack.auth.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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

  @PostMapping("/auth/register")
  public AuthResponse register(
      @Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
    AuthService.AuthResult result = authService.register(request);
    cookieService.addAccessTokenCookie(response, result.accessToken());

    return result.response();
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
}
