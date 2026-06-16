package com.offertrack.dashboard;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offertrack.applications.ApplicationStage;
import com.offertrack.auth.AuthService;
import com.offertrack.auth.CookieService;
import com.offertrack.auth.JwtAuthenticationFilter;
import com.offertrack.auth.JwtService;
import com.offertrack.config.SecurityConfig;
import com.offertrack.dashboard.dto.DashboardApplicationItemResponse;
import com.offertrack.dashboard.dto.DashboardInterviewItemResponse;
import com.offertrack.dashboard.dto.DashboardSummaryResponse;
import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class DashboardControllerSecurityTest {
  private static final String TEST_TOKEN = "test-token";
  private static final UUID AUTHENTICATED_USER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String AUTHENTICATED_USER_EMAIL = "user@example.com";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DashboardService dashboardService;
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
  void getSummaryReturnsForbiddenWhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isForbidden());
  }

  @Test
  void getSummaryReturnsOkForAuthenticatedUser() throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();
    DashboardSummaryResponse response =
        new DashboardSummaryResponse(
            8,
            3,
            2,
            1,
            4,
            1,
            0,
            1,
            0,
            8,
            9,
            3,
            List.of(
                new DashboardApplicationItemResponse(
                    applicationId,
                    "Acme",
                    "Backend Engineer",
                    ApplicationStage.INITIAL,
                    "https://example.com/job",
                    "Remote",
                    "remote",
                    null,
                    OffsetDateTime.parse("2026-05-01T10:15:00Z"))),
            List.of(),
            List.of(
                new DashboardInterviewItemResponse(
                    applicationId,
                    interviewId,
                    "Acme",
                    "Backend Engineer",
                    null,
                    null,
                    null,
                    OffsetDateTime.parse("2026-06-08T09:00:00Z"),
                    InterviewType.TECHNICAL,
                    InterviewStatus.SCHEDULED)),
            List.of());

    when(dashboardService.getSummary(eq(AUTHENTICATED_USER_ID))).thenReturn(response);

    mockMvc
        .perform(get("/api/dashboard/summary").cookie(accessTokenCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeProcesses").value(8))
        .andExpect(jsonPath("$.needsAttention").value(3))
        .andExpect(jsonPath("$.interviewing").value(2))
        .andExpect(jsonPath("$.offers").value(1))
        .andExpect(jsonPath("$.rejected").value(4))
        .andExpect(jsonPath("$.draftsToApplyCount").value(1))
        .andExpect(jsonPath("$.applicationsToFollowUpCount").value(0))
        .andExpect(jsonPath("$.upcomingInterviewsCount").value(1))
        .andExpect(jsonPath("$.interviewsToFollowUpCount").value(0))
        .andExpect(jsonPath("$.followUpAfterApplyingDays").value(8))
        .andExpect(jsonPath("$.upcomingInterviewDays").value(9))
        .andExpect(jsonPath("$.followUpAfterInterviewDays").value(3))
        .andExpect(jsonPath("$.draftsToApply[0].applicationId").value(applicationId.toString()))
        .andExpect(jsonPath("$.draftsToApply[0].stage").value("initial"))
        .andExpect(jsonPath("$.upcomingInterviews[0].interviewId").value(interviewId.toString()))
        .andExpect(jsonPath("$.upcomingInterviews[0].interviewType").value("technical"))
        .andExpect(jsonPath("$.upcomingInterviews[0].status").value("scheduled"));

    verify(dashboardService).getSummary(AUTHENTICATED_USER_ID);
  }

  private static Cookie accessTokenCookie() {
    return new Cookie(CookieService.ACCESS_TOKEN_COOKIE_NAME, TEST_TOKEN);
  }
}
