package com.offertrack;

import static com.offertrack.jooq.generated.tables.UserAuthTokens.USER_AUTH_TOKENS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offertrack.auth.AuthTokenPurpose;
import com.offertrack.auth.AuthTokenService;
import com.offertrack.auth.CookieService;
import com.offertrack.auth.PasswordService;
import com.offertrack.users.User;
import com.offertrack.users.UserRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {
  private static final String PASSWORD = "Password1";
  private static final Pattern EMAIL_TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordService passwordService;
  @Autowired private AuthTokenService authTokenService;
  @Autowired private DSLContext dsl;

  @MockitoBean private JavaMailSender mailSender;

  @BeforeEach
  void cleanDatabase() {
    reset(mailSender);
    dsl.execute("delete from user_auth_tokens");
    dsl.execute("delete from user_settings");
    dsl.execute("delete from application_interviews");
    dsl.execute("delete from job_applications");
    dsl.execute("delete from users");
  }

  @Test
  void registerCreatesUnverifiedUserTokenAndSendsEmailWithoutAuthCookie() throws Exception {
    String email = "register@example.com";

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailVerificationRequired").value(true))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

    User user = userRepository.findByEmail(email).orElseThrow();
    assertThat(user.emailVerifiedAt()).isNull();

    assertThat(
            dsl.fetchCount(
                USER_AUTH_TOKENS,
                USER_AUTH_TOKENS.USER_ID.eq(user.id()),
                USER_AUTH_TOKENS.PURPOSE.eq(AuthTokenPurpose.EMAIL_VERIFICATION.value())))
        .isEqualTo(1);

    String tokenHash =
        dsl.select(USER_AUTH_TOKENS.TOKEN_HASH)
            .from(USER_AUTH_TOKENS)
            .where(USER_AUTH_TOKENS.USER_ID.eq(user.id()))
            .fetchSingle(USER_AUTH_TOKENS.TOKEN_HASH);
    assertThat(tokenHash).hasSize(64);

    SimpleMailMessage message = captureOnlyMessage();
    assertThat(message.getTo()).containsExactly(email);
    assertThat(message.getText()).contains("/verify-email?token=");
  }

  @Test
  void loginBlocksUnverifiedUsersWithEmailNotVerifiedCode() throws Exception {
    createUnverifiedUser("blocked@example.com");

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("blocked@example.com")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
        .andExpect(cookie().doesNotExist(CookieService.ACCESS_TOKEN_COOKIE_NAME));
  }

  @Test
  void verifyEmailWithValidTokenSetsEmailVerifiedAtAndConsumesToken() throws Exception {
    RegistrationResult registration = registerViaApi("verify@example.com");

    mockMvc
        .perform(
            post("/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + registration.token() + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(true));

    User user = userRepository.findByEmail("verify@example.com").orElseThrow();
    assertThat(user.emailVerifiedAt()).isNotNull();

    OffsetDateTime consumedAt =
        dsl.select(USER_AUTH_TOKENS.CONSUMED_AT)
            .from(USER_AUTH_TOKENS)
            .where(USER_AUTH_TOKENS.USER_ID.eq(registration.userId()))
            .fetchSingle(USER_AUTH_TOKENS.CONSUMED_AT);
    assertThat(consumedAt).isNotNull();
  }

  @Test
  void verifyEmailRejectsInvalidExpiredAndConsumedTokens() throws Exception {
    expectInvalidToken("not-a-valid-token");

    User expiredUser = createUnverifiedUser("expired@example.com");
    String expiredToken = authTokenService.createEmailVerificationToken(expiredUser.id());
    dsl.update(USER_AUTH_TOKENS)
        .set(USER_AUTH_TOKENS.EXPIRES_AT, OffsetDateTime.now().minusMinutes(1))
        .where(USER_AUTH_TOKENS.USER_ID.eq(expiredUser.id()))
        .execute();
    expectInvalidToken(expiredToken);

    RegistrationResult consumed = registerViaApi("consumed@example.com");
    mockMvc
        .perform(
            post("/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + consumed.token() + "\"}"))
        .andExpect(status().isOk());
    expectInvalidToken(consumed.token());
  }

  @Test
  void resendVerificationReturnsGenericResponseForUnknownEmail() throws Exception {
    mockMvc
        .perform(
            post("/auth/email/verification/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"unknown@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    verifyNoInteractions(mailSender);
  }

  @Test
  void resendVerificationSendsNewTokenForExistingUnverifiedUser() throws Exception {
    User user = createUnverifiedUser("resend@example.com");

    mockMvc
        .perform(
            post("/auth/email/verification/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"resend@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    mockMvc
        .perform(
            post("/auth/email/verification/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"resend@example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    assertThat(dsl.fetchCount(USER_AUTH_TOKENS, USER_AUTH_TOKENS.USER_ID.eq(user.id())))
        .isEqualTo(2);
    verify(mailSender, times(2)).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
  }

  @Test
  void verifiedUserCanLogin() throws Exception {
    User user = createUnverifiedUser("verified@example.com");
    userRepository.markEmailVerified(user.id(), OffsetDateTime.now());

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("verified@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value("verified@example.com"))
        .andExpect(cookie().exists(CookieService.ACCESS_TOKEN_COOKIE_NAME));
  }

  private void expectInvalidToken(String token) throws Exception {
    mockMvc
        .perform(
            post("/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_AUTH_TOKEN"));
  }

  private RegistrationResult registerViaApi(String email) throws Exception {
    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email)))
        .andExpect(status().isOk());

    User user = userRepository.findByEmail(email).orElseThrow();
    return new RegistrationResult(user.id(), extractToken(captureOnlyMessage()));
  }

  private SimpleMailMessage captureOnlyMessage() {
    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(messageCaptor.capture());
    return messageCaptor.getValue();
  }

  private String extractToken(SimpleMailMessage message) {
    var matcher = EMAIL_TOKEN_PATTERN.matcher(message.getText());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private User createUnverifiedUser(String email) {
    return userRepository.createUser(email, passwordService.hash(PASSWORD), "Test User");
  }

  private static String registerJson(String email) {
    return """
        {
          "email": "%s",
          "password": "%s",
          "name": "Test User"
        }
        """
        .formatted(email, PASSWORD);
  }

  private static String loginJson(String email) {
    return """
        {
          "email": "%s",
          "password": "%s"
        }
        """
        .formatted(email, PASSWORD);
  }

  private record RegistrationResult(UUID userId, String token) {}
}
