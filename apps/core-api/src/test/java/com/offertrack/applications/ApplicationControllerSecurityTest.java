package com.offertrack.applications;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@WebMvcTest(controllers = ApplicationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ApplicationControllerSecurityTest {
  private static final String TEST_TOKEN = "test-token";
  private static final UUID AUTHENTICATED_USER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String AUTHENTICATED_USER_EMAIL = "user@example.com";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApplicationService applicationService;
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
  void patchStageReturnsNotFoundWhenServiceThrowsNotFound() throws Exception {
    UUID applicationId = UUID.randomUUID();
    when(applicationService.updateStage(eq(AUTHENTICATED_USER_ID), eq(applicationId), any()))
        .thenThrow(new ApplicationNotFoundException());

    mockMvc
        .perform(
            patch("/api/applications/{id}/stage", applicationId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"stage\":\"applied\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Application was not found"))
        .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId + "/stage"))
        .andExpect(jsonPath("$.timestamp").isString());
  }

  @Test
  void patchStageReturnsBadRequestWhenStageIsMissing() throws Exception {
    UUID applicationId = UUID.randomUUID();

    mockMvc
        .perform(
            patch("/api/applications/{id}/stage", applicationId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("stage")))
        .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId + "/stage"));
  }

  @Test
  void patchStageReturnsBadRequestForMalformedRequestBody() throws Exception {
    UUID applicationId = UUID.randomUUID();

    mockMvc
        .perform(
            patch("/api/applications/{id}/stage", applicationId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"stage\":\"unknown-stage\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
        .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId + "/stage"));
  }

  @Test
  void patchStageReturnsBadRequestForInvalidPathType() throws Exception {
    mockMvc
        .perform(
            patch("/api/applications/{id}/stage", "not-a-uuid")
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"stage\":\"applied\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.path").value("/api/applications/not-a-uuid/stage"));
  }

  @Test
  void patchStageReturnsSafeInternalErrorMessage() throws Exception {
    UUID applicationId = UUID.randomUUID();

    when(applicationService.updateStage(eq(AUTHENTICATED_USER_ID), eq(applicationId), any()))
        .thenThrow(new RuntimeException("sensitive failure details"));

    mockMvc
        .perform(
            patch("/api/applications/{id}/stage", applicationId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"stage\":\"applied\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("Something went wrong on the server. Try again."))
        .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId + "/stage"));
  }

  @Test
  void deleteReturnsNotFoundWhenServiceThrowsNotFound() throws Exception {
    UUID applicationId = UUID.randomUUID();
    doThrow(new ApplicationNotFoundException())
        .when(applicationService)
        .delete(eq(AUTHENTICATED_USER_ID), eq(applicationId));

    mockMvc
        .perform(delete("/api/applications/{id}", applicationId).cookie(accessTokenCookie()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"))
        .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId));
  }

  @Test
  void putReturnsNotFoundWhenServiceThrowsNotFound() throws Exception {
    UUID applicationId = UUID.randomUUID();
    when(applicationService.replace(eq(AUTHENTICATED_USER_ID), eq(applicationId), any()))
        .thenThrow(new ApplicationNotFoundException());

    mockMvc
        .perform(
            put("/api/applications/{id}", applicationId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {
                      "companyName": "Acme",
                      "positionTitle": "Backend Engineer",
                      "interviews": []
                    }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"))
        .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId));
  }

  @Test
  void putReturnsBadRequestWhenInterviewsAreMissing() throws Exception {
    UUID applicationId = UUID.randomUUID();

    mockMvc
        .perform(
            put("/api/applications/{id}", applicationId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {
                      "companyName": "Acme",
                      "positionTitle": "Backend Engineer"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("interviews")))
        .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId));
  }

  @Test
  void putReturnsBadRequestWhenJobUrlUsesUnsupportedScheme() throws Exception {
    expectPutJobUrlValidationError("ftp://example.com/jobs/123");
  }

  @Test
  void putReturnsBadRequestWhenJobUrlIsRelative() throws Exception {
    expectPutJobUrlValidationError("/jobs/123");
  }

  @Test
  void putReturnsBadRequestWhenJobUrlIsMalformed() throws Exception {
    expectPutJobUrlValidationError("https://exa mple.com/jobs/123");
  }

  @Test
  void putReturnsBadRequestWhenJobUrlIsTooLong() throws Exception {
    expectPutJobUrlValidationError("https://example.com/" + "a".repeat(2048));
  }

  @Test
  void putReturnsBadRequestWhenInterviewStatusIsMissing() throws Exception {
    UUID applicationId = UUID.randomUUID();

    mockMvc
        .perform(
            put("/api/applications/{id}", applicationId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {
                      "companyName": "Acme",
                      "positionTitle": "Backend Engineer",
                      "interviews": [
                        { "type": "technical" }
                      ]
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("interviews[0].status")))
        .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId));
  }

  @Test
  void putReturnsBadRequestWhenInterviewTypeIsMissing() throws Exception {
    UUID applicationId = UUID.randomUUID();

    mockMvc
        .perform(
            put("/api/applications/{id}", applicationId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {
                      "companyName": "Acme",
                      "positionTitle": "Backend Engineer",
                      "interviews": [
                        { "status": "planned" }
                      ]
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("interviews[0].type")))
        .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId));
  }

  @Test
  void removedGetByIdEndpointIsNotMapped() throws Exception {
    UUID applicationId = UUID.randomUUID();

    mockMvc
        .perform(get("/api/applications/{id}", applicationId).cookie(accessTokenCookie()))
        .andExpect(status().is4xxClientError());
  }

  private static Cookie accessTokenCookie() {
    return new Cookie(CookieService.ACCESS_TOKEN_COOKIE_NAME, TEST_TOKEN);
  }

  private void expectPutJobUrlValidationError(String jobUrl) throws Exception {
    UUID applicationId = UUID.randomUUID();

    mockMvc
        .perform(
            put("/api/applications/{id}", applicationId)
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {
                      "companyName": "Acme",
                      "positionTitle": "Backend Engineer",
                      "jobUrl": "%s",
                      "interviews": []
                    }
                    """
                        .formatted(jobUrl)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("jobUrl")))
        .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId));
  }
}
