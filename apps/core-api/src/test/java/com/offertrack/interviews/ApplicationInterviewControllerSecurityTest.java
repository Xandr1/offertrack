package com.offertrack.interviews;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offertrack.applications.ApplicationNotFoundException;
import com.offertrack.auth.AuthService;
import com.offertrack.auth.CookieService;
import com.offertrack.auth.JwtAuthenticationFilter;
import com.offertrack.auth.JwtService;
import com.offertrack.config.SecurityConfig;
import com.offertrack.interviews.dto.ApplicationInterviewResponse;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
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

@WebMvcTest(controllers = ApplicationInterviewController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ApplicationInterviewControllerSecurityTest {
  private static final String TEST_TOKEN = "test-token";
  private static final UUID AUTHENTICATED_USER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String AUTHENTICATED_USER_EMAIL = "user@example.com";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApplicationInterviewService applicationInterviewService;
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
  void getListReturnsForbiddenWhenUnauthenticated() throws Exception {
    UUID applicationId = UUID.randomUUID();

    mockMvc
        .perform(get("/api/applications/{applicationId}/interviews", applicationId))
        .andExpect(status().isForbidden());
  }

  @Test
  void getListReturnsOkForAuthenticatedOwner() throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.parse("2026-05-01T10:15:00Z");

    when(applicationInterviewService.list(eq(AUTHENTICATED_USER_ID), eq(applicationId)))
        .thenReturn(
            List.of(
                new ApplicationInterviewResponse(
                    interviewId,
                    applicationId,
                    InterviewType.RECRUITER,
                    InterviewStatus.SCHEDULED,
                    now,
                    now,
                    now)));

    mockMvc
        .perform(
            get("/api/applications/{applicationId}/interviews", applicationId)
                .cookie(accessTokenCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(interviewId.toString()))
        .andExpect(jsonPath("$[0].applicationId").value(applicationId.toString()))
        .andExpect(jsonPath("$[0].type").value("recruiter"))
        .andExpect(jsonPath("$[0].status").value("scheduled"));
  }

  @Test
  void getListReturnsNotFoundWhenServiceThrowsNotFound() throws Exception {
    UUID applicationId = UUID.randomUUID();

    when(applicationInterviewService.list(eq(AUTHENTICATED_USER_ID), eq(applicationId)))
        .thenThrow(new ApplicationNotFoundException());

    mockMvc
        .perform(
            get("/api/applications/{applicationId}/interviews", applicationId)
                .cookie(accessTokenCookie()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"))
        .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId + "/interviews"));
  }

  @Test
  void patchStatusReturnsBadRequestWhenStatusIsMissing() throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();

    mockMvc
        .perform(
            patch(
                    "/api/applications/{applicationId}/interviews/{interviewId}/status",
                    applicationId,
                    interviewId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("status")))
        .andExpect(
            jsonPath("$.path")
                .value(
                    "/api/applications/"
                        + applicationId
                        + "/interviews/"
                        + interviewId
                        + "/status"));
  }

  @Test
  void patchStatusReturnsBadRequestForMalformedRequestBody() throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();

    mockMvc
        .perform(
            patch(
                    "/api/applications/{applicationId}/interviews/{interviewId}/status",
                    applicationId,
                    interviewId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"status\":\"unknown-status\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
        .andExpect(
            jsonPath("$.path")
                .value(
                    "/api/applications/"
                        + applicationId
                        + "/interviews/"
                        + interviewId
                        + "/status"));
  }

  @Test
  void patchStatusReturnsBadRequestForInvalidPathType() throws Exception {
    UUID applicationId = UUID.randomUUID();

    mockMvc
        .perform(
            patch(
                    "/api/applications/{applicationId}/interviews/{interviewId}/status",
                    applicationId,
                    "not-a-uuid")
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"status\":\"passed\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(
            jsonPath("$.path")
                .value("/api/applications/" + applicationId + "/interviews/not-a-uuid/status"));
  }

  @Test
  void patchStatusReturnsNotFoundWhenServiceThrowsNotFound() throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();

    when(applicationInterviewService.updateStatus(
            eq(AUTHENTICATED_USER_ID), eq(applicationId), eq(interviewId), any()))
        .thenThrow(new InterviewNotFoundException());

    mockMvc
        .perform(
            patch(
                    "/api/applications/{applicationId}/interviews/{interviewId}/status",
                    applicationId,
                    interviewId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"status\":\"passed\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("INTERVIEW_NOT_FOUND"))
        .andExpect(
            jsonPath("$.path")
                .value(
                    "/api/applications/"
                        + applicationId
                        + "/interviews/"
                        + interviewId
                        + "/status"));
  }

  @Test
  void patchFollowUpReturnsUpdatedInterviewForAuthenticatedOwner() throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();
    OffsetDateTime followedUpAt = OffsetDateTime.parse("2026-05-01T10:15:00Z");
    when(applicationInterviewService.markFollowedUp(
            AUTHENTICATED_USER_ID, applicationId, interviewId))
        .thenReturn(
            new ApplicationInterviewResponse(
                interviewId,
                applicationId,
                InterviewType.TECHNICAL,
                InterviewStatus.SCHEDULED,
                followedUpAt.minusDays(3),
                followedUpAt,
                followedUpAt.minusDays(10),
                followedUpAt));

    mockMvc
        .perform(
            patch(
                    "/api/applications/{applicationId}/interviews/{interviewId}/follow-up",
                    applicationId,
                    interviewId)
                .cookie(accessTokenCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(interviewId.toString()))
        .andExpect(jsonPath("$.followedUpAt").value("2026-05-01T10:15:00Z"));
  }

  @Test
  void patchFollowUpReturnsNotFoundForMissingForeignOrWrongParentInterview() throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();
    when(applicationInterviewService.markFollowedUp(
            AUTHENTICATED_USER_ID, applicationId, interviewId))
        .thenThrow(new InterviewNotFoundException());

    mockMvc
        .perform(
            patch(
                    "/api/applications/{applicationId}/interviews/{interviewId}/follow-up",
                    applicationId,
                    interviewId)
                .cookie(accessTokenCookie()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INTERVIEW_NOT_FOUND"));
  }

  @Test
  void removedNestedInterviewWriteEndpointsAreNotMapped() throws Exception {
    UUID applicationId = UUID.randomUUID();
    UUID interviewId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/applications/{applicationId}/interviews", applicationId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"type\":\"technical\"}"))
        .andExpect(status().is4xxClientError());

    mockMvc
        .perform(
            put(
                    "/api/applications/{applicationId}/interviews/{interviewId}",
                    applicationId,
                    interviewId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"type\":\"technical\",\"status\":\"initial\"}"))
        .andExpect(status().is4xxClientError());

    mockMvc
        .perform(
            delete(
                    "/api/applications/{applicationId}/interviews/{interviewId}",
                    applicationId,
                    interviewId)
                .cookie(accessTokenCookie()))
        .andExpect(status().is4xxClientError());
  }

  private static Cookie accessTokenCookie() {
    return new Cookie(CookieService.ACCESS_TOKEN_COOKIE_NAME, TEST_TOKEN);
  }
}
