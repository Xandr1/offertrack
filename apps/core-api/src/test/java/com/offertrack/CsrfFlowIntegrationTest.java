package com.offertrack;

import static com.offertrack.jooq.generated.tables.UserSettings.USER_SETTINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertrack.auth.CookieService;
import com.offertrack.auth.JwtService;
import com.offertrack.auth.PasswordService;
import com.offertrack.ratelimit.RateLimitGuard;
import com.offertrack.users.User;
import com.offertrack.users.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CsrfFlowIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtService jwtService;
  @Autowired private PasswordService passwordService;
  @Autowired private UserRepository userRepository;
  @Autowired private DSLContext dsl;
  @MockitoBean private RateLimitGuard rateLimitGuard;

  @BeforeEach
  void cleanDatabase() {
    dsl.execute("delete from user_auth_tokens");
    dsl.execute("delete from user_settings");
    dsl.execute("delete from application_interviews");
    dsl.execute("delete from job_applications");
    dsl.execute("delete from users");
  }

  @Test
  void issuedMaskedJsonTokenSucceedsUnchangedOnCookieAuthenticatedMutation() throws Exception {
    User user = userRepository.createUser("csrf-flow@example.com", "unused-password-hash", "User");
    String accessToken = jwtService.generateAccessToken(user.id(), user.email());

    MvcResult csrfResult =
        mockMvc
            .perform(get("/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andReturn();

    JsonNode csrfBody = objectMapper.readTree(csrfResult.getResponse().getContentAsByteArray());
    String maskedToken = csrfBody.path("token").asText();
    Cookie repositoryCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
    assertThat(maskedToken).isNotBlank();
    assertThat(repositoryCookie).isNotNull();
    assertThat(repositoryCookie.isHttpOnly()).isTrue();
    assertThat(maskedToken).isNotEqualTo(repositoryCookie.getValue());

    mockMvc
        .perform(
            put("/api/settings")
                .cookie(
                    new Cookie(CookieService.ACCESS_TOKEN_COOKIE_NAME, accessToken),
                    repositoryCookie)
                .header("X-XSRF-TOKEN", maskedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "followUpAfterApplyingDays": 8,
                      "upcomingInterviewDays": 9,
                      "followUpAfterInterviewDays": 3,
                      "targetRole": "Platform Engineer"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetRole").value("Platform Engineer"));

    assertThat(dsl.fetchCount(USER_SETTINGS)).isEqualTo(1);
  }

  @Test
  void csrfRepositoryStateRotatesAcrossLoginAndLogoutBoundaries() throws Exception {
    String email = "csrf-rotation@example.com";
    String password = "Test-password-123!";
    User user = userRepository.createUser(email, passwordService.hash(password), "CSRF Test User");
    userRepository.markEmailVerified(user.id(), OffsetDateTime.now());

    IssuedCsrf initial = issueCsrf();
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/auth/login")
                    .cookie(initial.repositoryCookie())
                    .header("X-XSRF-TOKEN", initial.maskedToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """
                            .formatted(email, password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value(email))
            .andReturn();

    Cookie accessCookie =
        loginResult.getResponse().getCookie(CookieService.ACCESS_TOKEN_COOKIE_NAME);
    assertThat(accessCookie).isNotNull();

    IssuedCsrf postLogin = issueCsrf(accessCookie);
    assertRotated(initial, postLogin);

    performSettingsMutation(accessCookie, postLogin.repositoryCookie(), initial.maskedToken())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

    performSettingsMutation(accessCookie, postLogin.repositoryCookie(), postLogin.maskedToken())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetRole").value("Platform Engineer"));
    assertThat(dsl.fetchCount(USER_SETTINGS)).isEqualTo(1);

    mockMvc
        .perform(
            post("/auth/logout")
                .cookie(accessCookie, postLogin.repositoryCookie())
                .header("X-XSRF-TOKEN", postLogin.maskedToken()))
        .andExpect(status().isOk());

    IssuedCsrf postLogout = issueCsrf();
    assertRotated(postLogin, postLogout);

    mockMvc
        .perform(
            post("/auth/logout")
                .cookie(postLogout.repositoryCookie())
                .header("X-XSRF-TOKEN", postLogin.maskedToken()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

    mockMvc
        .perform(
            post("/auth/logout")
                .cookie(postLogout.repositoryCookie())
                .header("X-XSRF-TOKEN", postLogout.maskedToken()))
        .andExpect(status().isOk());
  }

  private IssuedCsrf issueCsrf(Cookie... cookies) throws Exception {
    var request = get("/auth/csrf");
    if (cookies.length > 0) {
      request.cookie(cookies);
    }

    MvcResult result =
        mockMvc
            .perform(request)
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    Cookie repositoryCookie = result.getResponse().getCookie("XSRF-TOKEN");
    assertThat(repositoryCookie).isNotNull();
    assertThat(repositoryCookie.isHttpOnly()).isTrue();
    assertThat(body.path("token").asText()).isNotBlank();
    return new IssuedCsrf(body.path("token").asText(), repositoryCookie);
  }

  private ResultActions performSettingsMutation(
      Cookie accessCookie, Cookie repositoryCookie, String maskedToken) throws Exception {
    return mockMvc.perform(
        put("/api/settings")
            .cookie(accessCookie, repositoryCookie)
            .header("X-XSRF-TOKEN", maskedToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "followUpAfterApplyingDays": 8,
                  "upcomingInterviewDays": 9,
                  "followUpAfterInterviewDays": 3,
                  "targetRole": "Platform Engineer"
                }
                """));
  }

  private static void assertRotated(IssuedCsrf before, IssuedCsrf after) {
    assertThat(after.repositoryCookie().getValue())
        .isNotEqualTo(before.repositoryCookie().getValue());
    assertThat(after.maskedToken()).isNotEqualTo(before.maskedToken());
  }

  private record IssuedCsrf(String maskedToken, Cookie repositoryCookie) {}
}
