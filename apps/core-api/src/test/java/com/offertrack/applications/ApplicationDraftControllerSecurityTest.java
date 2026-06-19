package com.offertrack.applications;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offertrack.applications.dto.ApplicationDraftInterviewResponse;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import com.offertrack.auth.AuthService;
import com.offertrack.auth.CookieService;
import com.offertrack.auth.JwtAuthenticationFilter;
import com.offertrack.auth.JwtService;
import com.offertrack.config.SecurityConfig;
import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ApplicationDraftController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ApplicationDraftControllerSecurityTest {
  private static final String TEST_TOKEN = "test-token";
  private static final UUID AUTHENTICATED_USER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String AUTHENTICATED_USER_EMAIL = "user@example.com";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApplicationDraftService applicationDraftService;
  @MockitoBean private AuthService authService;
  @MockitoBean private CookieService cookieService;
  @MockitoBean private JwtService jwtService;

  @BeforeEach
  void setUpAuthentication() {
    when(jwtService.isTokenValid(TEST_TOKEN)).thenReturn(true);
    when(jwtService.extractUserId(TEST_TOKEN)).thenReturn(AUTHENTICATED_USER_ID);
    when(jwtService.extractEmail(TEST_TOKEN)).thenReturn(AUTHENTICATED_USER_EMAIL);
  }

  @Test
  void postDraftReturnsForbiddenWhenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            post("/api/applications/draft")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"https://example.com/jobs/123\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void postDraftReturnsDraftForAuthenticatedUser() throws Exception {
    when(applicationDraftService.createDraft(any())).thenReturn(sampleDraftResponse());

    mockMvc
        .perform(
            post("/api/applications/draft")
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"example.com/jobs/123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companyName").value("Acme"))
        .andExpect(jsonPath("$.positionTitle").value("Backend Engineer"))
        .andExpect(jsonPath("$.jobUrl").value("https://example.com/jobs/123"))
        .andExpect(jsonPath("$.stage").value("initial"))
        .andExpect(jsonPath("$.interviews[0].type").value("technical"))
        .andExpect(jsonPath("$.interviews[0].status").value("planned"))
        .andExpect(jsonPath("$.interviews[0].scheduledAt").doesNotExist())
        .andExpect(jsonPath("$.warnings[0]").value("Location was not explicit."));

    verify(applicationDraftService).createDraft(any());
  }

  @Test
  void postDraftReturnsBadRequestForInvalidUrl() throws Exception {
    mockMvc
        .perform(
            post("/api/applications/draft")
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"ftp://example.com/jobs/123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("jobUrl")))
        .andExpect(jsonPath("$.path").value("/api/applications/draft"));

    verifyNoInteractions(applicationDraftService);
  }

  @Test
  void postDraftMapsParserUnavailableToBadGateway() throws Exception {
    when(applicationDraftService.createDraft(any())).thenThrow(new AiParserUnavailableException());

    mockMvc
        .perform(
            post("/api/applications/draft")
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"https://example.com/jobs/123\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.code").value("AI_PARSER_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("AI parser service is unavailable."));
  }

  @Test
  void postDraftMapsParserTimeoutToGatewayTimeout() throws Exception {
    when(applicationDraftService.createDraft(any())).thenThrow(new AiParserTimeoutException());

    mockMvc
        .perform(
            post("/api/applications/draft")
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"https://example.com/jobs/123\"}"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.status").value(504))
        .andExpect(jsonPath("$.code").value("AI_PARSER_TIMEOUT"))
        .andExpect(jsonPath("$.message").value("AI parser service timed out."));
  }

  @Test
  void postDraftMapsParserFetchFailedToBadGateway() throws Exception {
    when(applicationDraftService.createDraft(any())).thenThrow(new AiParserFetchFailedException());

    mockMvc
        .perform(
            post("/api/applications/draft")
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"https://example.com/jobs/123\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.code").value("AI_PARSER_FETCH_FAILED"))
        .andExpect(jsonPath("$.message").value("AI parser could not fetch the job URL."));
  }

  private static Cookie accessTokenCookie() {
    return new Cookie(CookieService.ACCESS_TOKEN_COOKIE_NAME, TEST_TOKEN);
  }

  private static ApplicationDraftResponse sampleDraftResponse() {
    return new ApplicationDraftResponse(
        "Acme",
        "Backend Engineer",
        "https://example.com/jobs/123",
        null,
        "remote",
        ApplicationStage.INITIAL,
        "Acme is hiring a backend engineer for API and platform work. The role is remote.",
        List.of(
            new ApplicationDraftInterviewResponse(
                InterviewType.TECHNICAL, InterviewStatus.PLANNED, null)),
        List.of("Location was not explicit."));
  }
}
