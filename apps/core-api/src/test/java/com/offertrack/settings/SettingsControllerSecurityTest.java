package com.offertrack.settings;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offertrack.auth.AuthService;
import com.offertrack.auth.CookieService;
import com.offertrack.auth.JwtAuthenticationFilter;
import com.offertrack.auth.JwtService;
import com.offertrack.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SettingsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SettingsControllerSecurityTest {
  private static final String TEST_TOKEN = "test-token";
  private static final UUID AUTHENTICATED_USER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String AUTHENTICATED_USER_EMAIL = "user@example.com";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SettingsService settingsService;
  @MockitoBean private AuthService authService;
  @MockitoBean private CookieService cookieService;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private com.offertrack.auth.AuthSessionService sessions;
  @MockitoBean private com.offertrack.auth.AuthSessionCleanup cleanup;
  @MockitoBean private com.offertrack.auth.AuthTokenCleanup tokenCleanup;

  @BeforeEach
  void setUpAuthentication() {
    when(jwtService.verify(TEST_TOKEN))
        .thenReturn(
            java.util.Optional.of(
                new JwtService.AccessClaims(
                    AUTHENTICATED_USER_ID,
                    UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    java.time.Instant.now(),
                    java.time.Instant.now().plusSeconds(900))));
    when(sessions.authenticates(org.mockito.ArgumentMatchers.any())).thenReturn(true);
  }

  @Test
  void getSettingsReturnsUnauthorizedWhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/settings")).andExpect(status().isUnauthorized());
  }

  @Test
  void getSettingsReturnsOkForAuthenticatedUser() throws Exception {
    when(settingsService.getSettings(eq(AUTHENTICATED_USER_ID)))
        .thenReturn(new UserSettings(AUTHENTICATED_USER_ID, 8, 9, 3, "Platform Engineer"));

    mockMvc
        .perform(get("/api/settings").cookie(accessTokenCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.followUpAfterApplyingDays").value(8))
        .andExpect(jsonPath("$.upcomingInterviewDays").value(9))
        .andExpect(jsonPath("$.followUpAfterInterviewDays").value(3))
        .andExpect(jsonPath("$.targetRole").value("Platform Engineer"));

    verify(settingsService).getSettings(AUTHENTICATED_USER_ID);
  }

  @Test
  void putSettingsReturnsOkForAuthenticatedUser() throws Exception {
    when(settingsService.updateSettings(eq(AUTHENTICATED_USER_ID), any()))
        .thenReturn(new UserSettings(AUTHENTICATED_USER_ID, 10, 12, 4, "Backend Engineer"));

    mockMvc
        .perform(
            put("/api/settings")
                .with(csrf())
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {
                      "followUpAfterApplyingDays": 10,
                      "upcomingInterviewDays": 12,
                      "followUpAfterInterviewDays": 4,
                      "targetRole": "Backend Engineer"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.followUpAfterApplyingDays").value(10))
        .andExpect(jsonPath("$.upcomingInterviewDays").value(12))
        .andExpect(jsonPath("$.followUpAfterInterviewDays").value(4))
        .andExpect(jsonPath("$.targetRole").value("Backend Engineer"));

    verify(settingsService).updateSettings(eq(AUTHENTICATED_USER_ID), any());
  }

  @Test
  void putSettingsReturnsBadRequestForInvalidThresholds() throws Exception {
    mockMvc
        .perform(
            put("/api/settings")
                .with(csrf())
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {
                      "followUpAfterApplyingDays": 0,
                      "upcomingInterviewDays": 61,
                      "followUpAfterInterviewDays": 31,
                      "targetRole": null
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("followUpAfterApplyingDays")))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("upcomingInterviewDays")))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("followUpAfterInterviewDays")))
        .andExpect(jsonPath("$.path").value("/api/settings"));
  }

  @Test
  void putSettingsReturnsBadRequestForTooLongTargetRole() throws Exception {
    mockMvc
        .perform(
            put("/api/settings")
                .with(csrf())
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {
                      "followUpAfterApplyingDays": 7,
                      "upcomingInterviewDays": 7,
                      "followUpAfterInterviewDays": 2,
                      "targetRole": "%s"
                    }
                    """
                        .formatted("a".repeat(161))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("targetRole")))
        .andExpect(jsonPath("$.path").value("/api/settings"));
  }

  private static Cookie accessTokenCookie() {
    return new Cookie(CookieService.ACCESS_TOKEN_COOKIE_NAME, TEST_TOKEN);
  }
}
