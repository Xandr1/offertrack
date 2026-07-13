package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offertrack.applications.dto.ApplicationDraftInterviewResponse;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import com.offertrack.auth.AuthService;
import com.offertrack.auth.CookieService;
import com.offertrack.auth.JwtAuthenticationFilter;
import com.offertrack.auth.JwtService;
import com.offertrack.config.RequestIdFilter;
import com.offertrack.config.SecurityConfig;
import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import com.offertrack.ratelimit.RateLimitExceededException;
import com.offertrack.ratelimit.RateLimitGuard;
import com.offertrack.ratelimit.RateLimitPolicy;
import com.offertrack.ratelimit.RateLimitSubjectType;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;

@WebMvcTest(
    controllers = ApplicationDraftController.class,
    properties = {"debug=false", "logging.level.org.springframework.web=INFO"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestIdFilter.class})
@ExtendWith(OutputCaptureExtension.class)
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
  @MockitoBean private RateLimitGuard rateLimitGuard;

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

    verifyNoInteractions(applicationDraftService, rateLimitGuard);
  }

  @Test
  void postDraftReturnsDraftForAuthenticatedUser() throws Exception {
    when(applicationDraftService.createDraft(any())).thenReturn(sampleDraftResponse());

    mockMvc
        .perform(
            post("/api/applications/draft")
                .with(csrf())
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"example.com/jobs/123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companyName").value("Acme"))
        .andExpect(jsonPath("$.positionTitle").value("Backend Engineer"))
        .andExpect(jsonPath("$.jobUrl").value("https://example.com/jobs/123"))
        .andExpect(jsonPath("$.stage").value("initial"))
        .andExpect(jsonPath("$.interviews[0].type").value("technical"))
        .andExpect(jsonPath("$.interviews[0].status").value("initial"))
        .andExpect(jsonPath("$.interviews[0].scheduledAt").doesNotExist())
        .andExpect(jsonPath("$.warnings[0]").value("Location was not explicit."));

    InOrder quotaBeforeCacheOrAi = inOrder(rateLimitGuard, applicationDraftService);
    quotaBeforeCacheOrAi.verify(rateLimitGuard).checkAiDraft(AUTHENTICATED_USER_ID);
    quotaBeforeCacheOrAi.verify(applicationDraftService).createDraft(any());
  }

  @Test
  void postDraftReturnsBadRequestForInvalidUrl() throws Exception {
    mockMvc
        .perform(
            post("/api/applications/draft")
                .with(csrf())
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"ftp://example.com/jobs/123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("jobUrl")))
        .andExpect(jsonPath("$.path").value("/api/applications/draft"));

    verifyNoInteractions(applicationDraftService, rateLimitGuard);
  }

  @Test
  void postDraftMapsAiServiceUnavailableToBadGateway() throws Exception {
    when(applicationDraftService.createDraft(any())).thenThrow(new AiServiceUnavailableException());

    mockMvc
        .perform(
            post("/api/applications/draft")
                .with(csrf())
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"https://example.com/jobs/123\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("AI service is unavailable."));
  }

  @Test
  void postDraftMapsAiServiceTimeoutToGatewayTimeout() throws Exception {
    when(applicationDraftService.createDraft(any())).thenThrow(new AiServiceTimeoutException());

    mockMvc
        .perform(
            post("/api/applications/draft")
                .with(csrf())
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"https://example.com/jobs/123\"}"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.status").value(504))
        .andExpect(jsonPath("$.code").value("AI_SERVICE_TIMEOUT"))
        .andExpect(jsonPath("$.message").value("AI service timed out."));
  }

  @Test
  void postDraftMapsAiServiceFetchFailedToBadGateway() throws Exception {
    when(applicationDraftService.createDraft(any())).thenThrow(new AiServiceFetchFailedException());

    mockMvc
        .perform(
            post("/api/applications/draft")
                .with(csrf())
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"https://example.com/jobs/123\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.code").value("AI_SERVICE_FETCH_FAILED"))
        .andExpect(jsonPath("$.message").value("AI service could not fetch the job URL."));
  }

  @Test
  void postDraftReturnsOneSafelyCorrelatedRateLimitResponseBeforeCallingAiService(
      CapturedOutput output) throws Exception {
    org.mockito.Mockito.doThrow(
            new RateLimitExceededException(
                List.of(RateLimitPolicy.AI_USER_MINUTE, RateLimitPolicy.AI_USER_DAY),
                List.of(RateLimitSubjectType.USER),
                42))
        .when(rateLimitGuard)
        .checkAiDraft(AUTHENTICATED_USER_ID);

    mockMvc
        .perform(
            post("/api/applications/draft")
                .with(csrf())
                .cookie(accessTokenCookie())
                .header("X-Request-Id", "draft-correlation-id")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"https://example.com/jobs/123\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "42"))
        .andExpect(header().string("X-Request-Id", "draft-correlation-id"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

    verifyNoInteractions(applicationDraftService);
    assertThat(output.getOut())
        .contains(
            "rate_limit_denied method=POST route=/api/applications/draft status=429",
            "error_code=RATE_LIMITED",
            "denied_policies=[ai-user-minute, ai-user-day]",
            "subject_types=[user]",
            "retry_after=42",
            "request_id=draft-correlation-id")
        .doesNotContain(
            AUTHENTICATED_USER_ID.toString(),
            AUTHENTICATED_USER_EMAIL,
            TEST_TOKEN,
            "https://example.com/jobs/123");
    assertThat(StringUtils.countOccurrencesOf(output.getOut(), "rate_limit_denied")).isEqualTo(1);
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
                InterviewType.TECHNICAL, InterviewStatus.INITIAL, null)),
        List.of("Location was not explicit."));
  }
}
