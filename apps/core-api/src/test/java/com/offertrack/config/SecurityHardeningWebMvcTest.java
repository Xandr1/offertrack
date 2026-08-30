package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertrack.auth.AuthController;
import com.offertrack.auth.AuthService;
import com.offertrack.auth.CookieService;
import com.offertrack.auth.JwtAuthenticationFilter;
import com.offertrack.auth.JwtService;
import com.offertrack.auth.dto.AuthResponse;
import com.offertrack.auth.dto.GenericSuccessResponse;
import com.offertrack.auth.dto.RegisterResponse;
import com.offertrack.ratelimit.RateLimitGuard;
import com.offertrack.settings.SettingsController;
import com.offertrack.settings.SettingsService;
import com.offertrack.settings.UserSettings;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
    controllers = {AuthController.class, SettingsController.class},
    properties =
        "app.management.dependency-health-key=dependency-health-key-which-is-long-enough-and-distinct")
@Import({
  SecurityConfig.class,
  JwtAuthenticationFilter.class,
  CookieService.class,
  SecurityHardeningWebMvcTest.DependencyHealthController.class
})
class SecurityHardeningWebMvcTest {
  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String ACCESS_TOKEN = "valid-access-token";
  private static final String DEPENDENCY_HEALTH_KEY =
      "dependency-health-key-which-is-long-enough-and-distinct";
  private static final String SETTINGS_JSON =
      """
      {
        "followUpAfterApplyingDays": 7,
        "upcomingInterviewDays": 7,
        "followUpAfterInterviewDays": 2,
        "targetRole": "Backend Engineer"
      }
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AuthService authService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private SettingsService settingsService;
  @MockitoBean private RateLimitGuard rateLimitGuard;

  @BeforeEach
  void configureJwt() {
    when(jwtService.isTokenValid(ACCESS_TOKEN)).thenReturn(true);
    when(jwtService.extractUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
    when(jwtService.extractEmail(ACCESS_TOKEN)).thenReturn("user@example.com");
    when(settingsService.updateSettings(eq(USER_ID), any()))
        .thenReturn(new UserSettings(USER_ID, 7, 7, 2, "Backend Engineer"));
  }

  @Test
  void issuesMaskedHttpOnlyTokenAndAcceptsJsonValueUnchanged() throws Exception {
    IssuedCsrf issued = issueCsrf();

    assertThat(issued.repositoryCookie().isHttpOnly()).isTrue();
    assertThat(issued.maskedToken()).isNotEqualTo(issued.repositoryCookie().getValue());

    mockMvc
        .perform(
            put("/api/settings")
                .cookie(accessCookie(), issued.repositoryCookie())
                .header("X-XSRF-TOKEN", issued.maskedToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetRole").value("Backend Engineer"));
  }

  @Test
  void appliesFinalCsrfMatcherAuthenticationMatrix() throws Exception {
    mockMvc
        .perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(JsonCsrfAccessDeniedHandler.ERROR_CODE));
    verifyNoInteractions(rateLimitGuard);

    mockMvc
        .perform(
            put("/api/settings")
                .cookie(new Cookie(CookieService.ACCESS_TOKEN_COOKIE_NAME, "invalid-access-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS_JSON))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(JsonCsrfAccessDeniedHandler.ERROR_CODE));

    mockMvc
        .perform(
            put("/api/settings").contentType(MediaType.APPLICATION_JSON).content(SETTINGS_JSON))
        .andExpect(status().isForbidden())
        .andExpect(content().string(""));

    mockMvc
        .perform(
            put("/api/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS_JSON))
        .andExpect(status().isForbidden())
        .andExpect(content().string(""));

    mockMvc
        .perform(
            put("/api/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS_JSON))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            put("/api/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .cookie(accessCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(SETTINGS_JSON))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(JsonCsrfAccessDeniedHandler.ERROR_CODE));
  }

  @Test
  void successfulLoginClearsRepositoryTokenAndReplacementIsLazy() throws Exception {
    IssuedCsrf issued = issueCsrf();
    when(authService.login(any()))
        .thenReturn(
            new AuthService.AuthResult(
                ACCESS_TOKEN,
                new AuthResponse(
                    new AuthResponse.UserSummary(USER_ID, "user@example.com", "OfferTrack User"))));

    MvcResult login =
        mockMvc
            .perform(
                post("/auth/login")
                    .cookie(issued.repositoryCookie())
                    .header("X-XSRF-TOKEN", issued.maskedToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"user@example.com","password":"correct-password"}
                        """))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(login.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
        .anyMatch(
            value ->
                value.startsWith("XSRF-TOKEN=")
                    && value.contains("Max-Age=0")
                    && value.contains("HttpOnly"));

    mockMvc
        .perform(
            post("/auth/logout")
                .header("X-XSRF-TOKEN", issued.maskedToken())
                .cookie(accessCookie()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(JsonCsrfAccessDeniedHandler.ERROR_CODE));

    IssuedCsrf replacement = issueCsrf();
    mockMvc
        .perform(
            post("/auth/logout")
                .header("X-XSRF-TOKEN", replacement.maskedToken())
                .cookie(accessCookie(), replacement.repositoryCookie()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/auth/logout")
                .header("X-XSRF-TOKEN", replacement.maskedToken())
                .cookie(accessCookie()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(JsonCsrfAccessDeniedHandler.ERROR_CODE));

    IssuedCsrf afterLogout = issueCsrf();
    mockMvc
        .perform(
            post("/auth/logout")
                .header("X-XSRF-TOKEN", afterLogout.maskedToken())
                .cookie(accessCookie(), afterLogout.repositoryCookie()))
        .andExpect(status().isOk());
  }

  @Test
  void configuresCorsAndBaselineHeaders() throws Exception {
    mockMvc
        .perform(
            options("/api/settings")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                .header(
                    HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                    "Content-Type,X-XSRF-TOKEN,X-Request-Id"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

    mockMvc
        .perform(get("/auth/csrf").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Retry-After, X-Request-Id"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(
            header()
                .string(
                    "Permissions-Policy",
                    "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
        .andExpect(header().doesNotExist("Strict-Transport-Security"));

    mockMvc
        .perform(
            options("/api/settings")
                .header(HttpHeaders.ORIGIN, "https://unknown.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
        .andExpect(status().isForbidden());
  }

  @Test
  void allowsDependencyHealthWithTheDedicatedKey() throws Exception {
    mockMvc
        .perform(
            get(DependencyHealthAuthenticationFilter.PATH)
                .header(DependencyHealthAuthenticationFilter.HEADER, DEPENDENCY_HEALTH_KEY))
        .andExpect(status().isOk())
        .andExpect(content().string("UP"));
  }

  @Test
  void rejectsDependencyHealthWithoutTheDedicatedKey() throws Exception {
    mockMvc
        .perform(get(DependencyHealthAuthenticationFilter.PATH))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsDependencyHealthWithTheWrongKey() throws Exception {
    mockMvc
        .perform(
            get(DependencyHealthAuthenticationFilter.PATH)
                .header(DependencyHealthAuthenticationFilter.HEADER, "wrong-health-key"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsNormalUserAuthenticationWithoutTheDedicatedKey() throws Exception {
    mockMvc
        .perform(
            get(DependencyHealthAuthenticationFilter.PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get(DependencyHealthAuthenticationFilter.PATH).cookie(accessCookie()))
        .andExpect(status().isForbidden());
  }

  @Test
  void validBoundAuthRequestsDelegateToEndpointRateLimitGuards() throws Exception {
    IssuedCsrf issued = issueCsrf();
    String remoteAddress = "203.0.113.42";
    when(authService.register(any())).thenReturn(new RegisterResponse(true));
    when(authService.resendVerificationEmail(any())).thenReturn(new GenericSuccessResponse(true));
    when(authService.forgotPassword(any())).thenReturn(new GenericSuccessResponse(true));
    when(authService.resetPassword(any())).thenReturn(new GenericSuccessResponse(true));
    when(authService.login(any()))
        .thenReturn(
            new AuthService.AuthResult(
                ACCESS_TOKEN,
                new AuthResponse(
                    new AuthResponse.UserSummary(USER_ID, "user@example.com", "OfferTrack User"))));

    mockMvc
        .perform(
            csrfPost(
                "/auth/register",
                issued,
                """
                {"email":"user@example.com","password":"Valid123","name":"User"}
                """,
                remoteAddress))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            csrfPost(
                "/auth/email/verification/resend",
                issued,
                """
                {"email":"user@example.com"}
                """,
                remoteAddress))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            csrfPost(
                "/auth/password/forgot",
                issued,
                """
                {"email":"user@example.com"}
                """,
                remoteAddress))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            csrfPost(
                "/auth/password/reset",
                issued,
                """
                {"token":"reset-token-value","newPassword":"Valid123"}
                """,
                remoteAddress))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            csrfPost(
                "/auth/login",
                issued,
                """
                {"email":"user@example.com","password":"Valid123"}
                """,
                remoteAddress))
        .andExpect(status().isOk());

    verify(rateLimitGuard).checkRegistration("user@example.com", remoteAddress);
    verify(rateLimitGuard).checkVerificationResend("user@example.com", remoteAddress);
    verify(rateLimitGuard).checkForgotPassword("user@example.com", remoteAddress);
    verify(rateLimitGuard).checkPasswordReset("reset-token-value", remoteAddress);
    verify(rateLimitGuard).checkLogin("user@example.com", remoteAddress);
  }

  private IssuedCsrf issueCsrf() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.token").isString())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    Cookie repositoryCookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(repositoryCookie).isNotNull();
    assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
        .anyMatch(value -> value.startsWith("XSRF-TOKEN=") && value.contains("HttpOnly"));
    return new IssuedCsrf(body.get("token").asText(), repositoryCookie);
  }

  private static Cookie accessCookie() {
    return new Cookie(CookieService.ACCESS_TOKEN_COOKIE_NAME, ACCESS_TOKEN);
  }

  private static MockHttpServletRequestBuilder csrfPost(
      String path, IssuedCsrf issued, String content, String remoteAddress) {
    return post(path)
        .with(
            request -> {
              request.setRemoteAddr(remoteAddress);
              return request;
            })
        .cookie(issued.repositoryCookie())
        .header("X-XSRF-TOKEN", issued.maskedToken())
        .contentType(MediaType.APPLICATION_JSON)
        .content(content);
  }

  @RestController
  public static class DependencyHealthController {
    @GetMapping(DependencyHealthAuthenticationFilter.PATH)
    String dependencies() {
      return "UP";
    }
  }

  private record IssuedCsrf(String maskedToken, Cookie repositoryCookie) {}
}
