package com.offertrack;

import static com.offertrack.jooq.generated.tables.UserSettings.USER_SETTINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertrack.auth.CookieService;
import com.offertrack.auth.JwtService;
import com.offertrack.users.User;
import com.offertrack.users.UserRepository;
import jakarta.servlet.http.Cookie;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CsrfFlowIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtService jwtService;
  @Autowired private UserRepository userRepository;
  @Autowired private DSLContext dsl;

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
}
