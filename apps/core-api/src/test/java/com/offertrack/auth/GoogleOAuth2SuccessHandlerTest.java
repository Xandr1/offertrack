package com.offertrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.offertrack.auth.dto.AuthResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2SuccessHandlerTest {
  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Mock private AuthService authService;

  private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository =
      new CookieOAuth2AuthorizationRequestRepository("test-oauth-cookie-secret");
  private GoogleOAuth2SuccessHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new GoogleOAuth2SuccessHandler(
            authService,
            new CookieService(),
            authorizationRequestRepository,
            "http://localhost:3000/");
  }

  @Test
  void validOidcUserSetsAccessTokenClearsOAuthCookieAndRedirectsToDashboard() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(authService.loginWithGoogle("user@example.com", "Google User", true))
        .thenReturn(
            new AuthService.AuthResult(
                "jwt-token",
                new AuthResponse(
                    new AuthResponse.UserSummary(USER_ID, "user@example.com", "Google User"))));

    handler.onAuthenticationSuccess(
        new MockHttpServletRequest(),
        response,
        googleAuthentication(oidcUser("user@example.com", true)));

    assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/dashboard");
    assertThat(hasAccessTokenCookie(response)).isTrue();
    assertThat(hasClearingOAuthCookie(response)).isTrue();
    verify(authService).loginWithGoogle("user@example.com", "Google User", true);
  }

  @Test
  void blankEmailFailsWithoutSettingAccessToken() throws Exception {
    expectFailure(googleAuthentication(oidcUser(" ", true)), false);
  }

  @Test
  void missingEmailFailsWithoutSettingAccessToken() throws Exception {
    expectFailure(googleAuthentication(oidcUser(null, true)), false);
  }

  @Test
  void unverifiedEmailFailsWithoutSettingAccessToken() throws Exception {
    expectFailure(googleAuthentication(oidcUser("user@example.com", false)), false);
  }

  @Test
  void nullEmailVerifiedFailsWithoutSettingAccessToken() throws Exception {
    expectFailure(googleAuthentication(oidcUser("user@example.com", null)), false);
  }

  @Test
  void wrongRegistrationIdFailsWithoutSettingAccessToken() throws Exception {
    expectFailure(oauth2Authentication("github", oidcUser("user@example.com", true)), false);
  }

  @Test
  void nonOidcPrincipalFailsWithoutSettingAccessToken() throws Exception {
    expectFailure(new TestingAuthenticationToken("user@example.com", "credentials"), false);
  }

  @Test
  void nonOidcOAuthPrincipalFailsWithoutSettingAccessToken() throws Exception {
    DefaultOAuth2User principal =
        new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("sub", "google-subject"),
            "sub");

    expectFailure(
        new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"), false);
  }

  @Test
  void serviceFailureFailsWithoutSettingAccessToken() throws Exception {
    when(authService.loginWithGoogle(anyString(), anyString(), anyBoolean()))
        .thenThrow(new IllegalStateException("failed"));

    expectFailure(googleAuthentication(oidcUser("user@example.com", true)), true);
  }

  private void expectFailure(Authentication authentication, boolean authServiceExpected)
      throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

    assertThat(response.getRedirectedUrl())
        .isEqualTo("http://localhost:3000/login?oauthError=google");
    assertThat(hasAccessTokenCookie(response)).isFalse();
    assertThat(hasClearingOAuthCookie(response)).isTrue();

    if (!authServiceExpected) {
      verifyNoInteractions(authService);
    }
  }

  private static Authentication googleAuthentication(OidcUser oidcUser) {
    return oauth2Authentication("google", oidcUser);
  }

  private static Authentication oauth2Authentication(String registrationId, OidcUser oidcUser) {
    return new OAuth2AuthenticationToken(
        oidcUser, List.copyOf(oidcUser.getAuthorities()), registrationId);
  }

  private static OidcUser oidcUser(String email, Boolean emailVerified) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", "google-subject");
    claims.put("name", "Google User");

    if (email != null) {
      claims.put("email", email);
    }

    if (emailVerified != null) {
      claims.put("email_verified", emailVerified);
    }

    OidcIdToken idToken =
        new OidcIdToken(
            "id-token", Instant.now(), Instant.now().plusSeconds(60), Map.copyOf(claims));

    return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
  }

  private static boolean hasAccessTokenCookie(MockHttpServletResponse response) {
    return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
        .anyMatch(header -> header.contains(CookieService.ACCESS_TOKEN_COOKIE_NAME + "=jwt-token"));
  }

  private static boolean hasClearingOAuthCookie(MockHttpServletResponse response) {
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
