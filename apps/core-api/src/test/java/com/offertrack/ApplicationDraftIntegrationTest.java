package com.offertrack;

import static com.offertrack.jooq.generated.tables.ApplicationInterviews.APPLICATION_INTERVIEWS;
import static com.offertrack.jooq.generated.tables.JobApplications.JOB_APPLICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offertrack.applications.AiServiceClient;
import com.offertrack.applications.ApplicationStage;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import com.offertrack.auth.CookieService;
import com.offertrack.auth.JwtService;
import com.offertrack.ratelimit.RateLimitGuard;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {"management.health.mail.enabled=false", "app.ai-draft-cache.enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationDraftIntegrationTest {
  private static final UUID AUTHENTICATED_USER_ID =
      UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String AUTHENTICATED_USER_EMAIL = "user@example.com";

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private DSLContext dsl;

  @MockitoBean private AiServiceClient aiServiceClient;
  @MockitoBean private RateLimitGuard rateLimitGuard;

  @BeforeEach
  void cleanDatabase() {
    dsl.execute("delete from application_interviews");
    dsl.execute("delete from job_applications");
    dsl.execute("delete from users");
  }

  @Test
  void authenticatedDraftRequestCallsAiServiceAndDoesNotCreateRecords() throws Exception {
    when(aiServiceClient.parseJob(any()))
        .thenReturn(
            new ApplicationDraftResponse(
                "Acme",
                "Backend Engineer",
                "https://example.com/jobs/123",
                "Remote",
                "remote",
                ApplicationStage.INITIAL,
                "Acme is hiring a backend engineer for API and platform work. The role is remote.",
                List.of(),
                List.of()));

    mockMvc
        .perform(
            post("/api/applications/draft")
                .with(csrf())
                .cookie(accessTokenCookie())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"jobUrl\":\"example.com/jobs/123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companyName").value("Acme"))
        .andExpect(jsonPath("$.jobUrl").value("https://example.com/jobs/123"))
        .andExpect(jsonPath("$.stage").value("initial"));

    verify(aiServiceClient).parseJob(any());
    assertThat(dsl.fetchCount(JOB_APPLICATIONS)).isZero();
    assertThat(dsl.fetchCount(APPLICATION_INTERVIEWS)).isZero();
  }

  private jakarta.servlet.http.Cookie accessTokenCookie() {
    return new jakarta.servlet.http.Cookie(
        CookieService.ACCESS_TOKEN_COOKIE_NAME,
        jwtService.generateAccessToken(AUTHENTICATED_USER_ID, AUTHENTICATED_USER_EMAIL));
  }
}
