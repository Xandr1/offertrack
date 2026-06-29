package com.offertrack.applications;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offertrack.applications.dto.ApplicationBoardColumnResponse;
import com.offertrack.applications.dto.ApplicationBoardResponse;
import com.offertrack.applications.dto.ApplicationResponse;
import com.offertrack.auth.AuthService;
import com.offertrack.auth.CookieService;
import com.offertrack.auth.JwtAuthenticationFilter;
import com.offertrack.auth.JwtService;
import com.offertrack.config.SecurityConfig;
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
  void getReturnsApplicationForAuthenticatedUser() throws Exception {
    UUID applicationId = UUID.randomUUID();
    when(applicationService.get(AUTHENTICATED_USER_ID, applicationId))
        .thenReturn(sampleApplicationResponse(applicationId));

    mockMvc
        .perform(get("/api/applications/{id}", applicationId).cookie(accessTokenCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(applicationId.toString()))
        .andExpect(jsonPath("$.companyName").value("Acme"))
        .andExpect(jsonPath("$.positionTitle").value("Backend Engineer"));
  }

  @Test
  void getReturnsNotFoundWhenServiceThrowsNotFound() throws Exception {
    UUID applicationId = UUID.randomUUID();
    when(applicationService.get(AUTHENTICATED_USER_ID, applicationId))
        .thenThrow(new ApplicationNotFoundException());

    mockMvc
        .perform(get("/api/applications/{id}", applicationId).cookie(accessTokenCookie()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"))
        .andExpect(jsonPath("$.path").value("/api/applications/" + applicationId));
  }

  @Test
  void getReturnsBadRequestForInvalidPathType() throws Exception {
    mockMvc
        .perform(get("/api/applications/{id}", "not-a-uuid").cookie(accessTokenCookie()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.path").value("/api/applications/not-a-uuid"));
  }

  @Test
  void listReturnsBadRequestForInvalidParams() throws Exception {
    expectInvalidListParam("page", "-1");
    expectInvalidListParam("size", "0");
    expectInvalidListParam("size", "101");
    expectInvalidListParam("stage", "");
    expectInvalidListParam("stage", "unknown");
    expectInvalidListParam("sort", "");
    expectInvalidListParam("sort", "notes");
    expectInvalidListParam("sort", "companyName");
    expectInvalidListParam("sort", "positionTitle");
    expectInvalidListParam("sort", "stage");
    expectInvalidListParam("direction", "");
    expectInvalidListParam("direction", "sideways");
  }

  @Test
  void listAcceptsSupportedSorts() throws Exception {
    mockMvc
        .perform(
            get("/api/applications")
                .param("sort", "updatedAt")
                .param("direction", "desc")
                .cookie(accessTokenCookie()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/applications")
                .param("sort", "createdAt")
                .param("direction", "asc")
                .cookie(accessTokenCookie()))
        .andExpect(status().isOk());
  }

  @Test
  void boardReturnsColumnsForAuthenticatedUser() throws Exception {
    ApplicationResponse application = sampleApplicationResponse(UUID.randomUUID());
    when(applicationService.board(eq(AUTHENTICATED_USER_ID), any()))
        .thenReturn(
            new ApplicationBoardResponse(
                List.of(
                    new ApplicationBoardColumnResponse(
                        ApplicationStage.APPLIED, 21, List.of(application), 1, true))));

    mockMvc
        .perform(
            get("/api/applications/board")
                .param("search", " acme ")
                .param("sort", "createdAt")
                .param("direction", "asc")
                .cookie(accessTokenCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.columns[0].stage").value("applied"))
        .andExpect(jsonPath("$.columns[0].totalCount").value(21))
        .andExpect(jsonPath("$.columns[0].items[0].companyName").value("Acme"))
        .andExpect(jsonPath("$.columns[0].nextOffset").value(1))
        .andExpect(jsonPath("$.columns[0].hasMore").value(true));

    verify(applicationService)
        .board(
            AUTHENTICATED_USER_ID,
            new ApplicationBoardQuery(
                "acme",
                0,
                ApplicationListQuery.ApplicationSort.CREATED_AT,
                ApplicationListQuery.SortDirection.ASC));
  }

  @Test
  void boardAcceptsUpdatedAtSort() throws Exception {
    when(applicationService.board(eq(AUTHENTICATED_USER_ID), any()))
        .thenReturn(new ApplicationBoardResponse(List.of()));

    mockMvc
        .perform(
            get("/api/applications/board")
                .param("sort", "updatedAt")
                .param("direction", "desc")
                .cookie(accessTokenCookie()))
        .andExpect(status().isOk());

    verify(applicationService)
        .board(
            AUTHENTICATED_USER_ID,
            new ApplicationBoardQuery(
                null,
                0,
                ApplicationListQuery.ApplicationSort.UPDATED_AT,
                ApplicationListQuery.SortDirection.DESC));
  }

  @Test
  void boardColumnReturnsRequestedPage() throws Exception {
    when(applicationService.boardColumn(
            eq(AUTHENTICATED_USER_ID), eq(ApplicationStage.OFFER), any()))
        .thenReturn(
            new ApplicationBoardColumnResponse(ApplicationStage.OFFER, 25, List.of(), 20, true));

    mockMvc
        .perform(
            get("/api/applications/board/columns/offer")
                .param("offset", "20")
                .param("sort", "createdAt")
                .param("direction", "asc")
                .cookie(accessTokenCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stage").value("offer"))
        .andExpect(jsonPath("$.totalCount").value(25))
        .andExpect(jsonPath("$.nextOffset").value(20));

    verify(applicationService)
        .boardColumn(
            AUTHENTICATED_USER_ID,
            ApplicationStage.OFFER,
            new ApplicationBoardQuery(
                null,
                20,
                ApplicationListQuery.ApplicationSort.CREATED_AT,
                ApplicationListQuery.SortDirection.ASC));
  }

  @Test
  void boardColumnReturnsBadRequestForInvalidStageOrOffset() throws Exception {
    expectInvalidBoardColumn("unknown", "0");
    expectInvalidBoardColumn("applied", "-1");
    expectInvalidBoardColumn("applied", "not-a-number");
  }

  @Test
  void boardReturnsBadRequestForUnsupportedSortOrDirection() throws Exception {
    expectInvalidBoardParam("sort", "companyName");
    expectInvalidBoardParam("sort", "positionTitle");
    expectInvalidBoardParam("sort", "stage");
    expectInvalidBoardParam("direction", "sideways");
    expectInvalidBoardColumnParam("sort", "companyName");
    expectInvalidBoardColumnParam("direction", "sideways");
  }

  private static Cookie accessTokenCookie() {
    return new Cookie(CookieService.ACCESS_TOKEN_COOKIE_NAME, TEST_TOKEN);
  }

  private static ApplicationResponse sampleApplicationResponse(UUID id) {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-01T10:15:00Z");

    return new ApplicationResponse(
        id,
        "Acme",
        "Backend Engineer",
        null,
        "Warsaw",
        "hybrid",
        ApplicationStage.APPLIED,
        null,
        null,
        now,
        now,
        null);
  }

  private void expectInvalidListParam(String name, String value) throws Exception {
    mockMvc
        .perform(get("/api/applications").param(name, value).cookie(accessTokenCookie()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.path").value("/api/applications"));
  }

  private void expectInvalidBoardColumn(String stage, String offset) throws Exception {
    mockMvc
        .perform(
            get("/api/applications/board/columns/{stage}", stage)
                .param("offset", offset)
                .cookie(accessTokenCookie()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.path").value("/api/applications/board/columns/" + stage));
  }

  private void expectInvalidBoardParam(String name, String value) throws Exception {
    mockMvc
        .perform(get("/api/applications/board").param(name, value).cookie(accessTokenCookie()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.path").value("/api/applications/board"));
  }

  private void expectInvalidBoardColumnParam(String name, String value) throws Exception {
    mockMvc
        .perform(
            get("/api/applications/board/columns/applied")
                .param(name, value)
                .cookie(accessTokenCookie()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.path").value("/api/applications/board/columns/applied"));
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
