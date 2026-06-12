package com.offertrack;

import static com.offertrack.jooq.generated.tables.UserAuthTokens.USER_AUTH_TOKENS;
import static com.offertrack.jooq.generated.tables.Users.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import java.util.List;
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
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {
  private static final String PASSWORD = "Password1";
  private static final String NEW_PASSWORD = "NewPassword1";
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
  void plusAliasEmailsAreDistinctForVerificationAndPasswordReset() throws Exception {
    String baseEmail = "john@example.com";
    String aliasEmail = "john+1@example.com";

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(baseEmail)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(aliasEmail)))
        .andExpect(status().isOk());

    User baseUser = userRepository.findByEmail(baseEmail).orElseThrow();
    User aliasUser = userRepository.findByEmail(aliasEmail).orElseThrow();
    assertThat(baseUser.id()).isNotEqualTo(aliasUser.id());
    assertThat(baseUser.emailVerifiedAt()).isNull();
    assertThat(aliasUser.emailVerifiedAt()).isNull();

    List<SimpleMailMessage> registrationMessages = captureMessages(2);
    assertThat(registrationMessages.get(0).getTo()).containsExactly(baseEmail);
    assertThat(registrationMessages.get(1).getTo()).containsExactly(aliasEmail);

    String aliasVerificationToken = extractToken(registrationMessages.get(1));
    mockMvc
        .perform(
            post("/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + aliasVerificationToken + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(true));

    assertThat(userRepository.findByEmail(baseEmail).orElseThrow().emailVerifiedAt()).isNull();
    assertThat(userRepository.findByEmail(aliasEmail).orElseThrow().emailVerifiedAt()).isNotNull();

    reset(mailSender);
    mockMvc
        .perform(
            post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotPasswordJson(baseEmail)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    SimpleMailMessage resetMessage = captureOnlyMessage();
    assertThat(resetMessage.getTo()).containsExactly(baseEmail);
    assertThat(resetMessage.getText()).contains("/reset-password?token=");

    resetPasswordViaApi(extractToken(resetMessage), NEW_PASSWORD);

    User updatedBaseUser = userRepository.findByEmail(baseEmail).orElseThrow();
    User unchangedAliasUser = userRepository.findByEmail(aliasEmail).orElseThrow();
    assertThat(passwordService.matches(NEW_PASSWORD, updatedBaseUser.passwordHash())).isTrue();
    assertThat(passwordService.matches(PASSWORD, unchangedAliasUser.passwordHash())).isTrue();
    assertThat(passwordService.matches(NEW_PASSWORD, unchangedAliasUser.passwordHash())).isFalse();
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
  void loginReturnsGenericInvalidCredentialsForUserWithoutPasswordHash() throws Exception {
    User user = createVerifiedUser("oauth-only@example.com");
    setPasswordHash(user.id(), null);

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("oauth-only@example.com")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("REQUEST_FAILED"))
        .andExpect(jsonPath("$.message").value("Invalid email or password"))
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
  void forgotPasswordReturnsGenericResponseForUnknownEmail() throws Exception {
    mockMvc
        .perform(
            post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotPasswordJson("unknown@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    assertThat(
            dsl.fetchCount(
                USER_AUTH_TOKENS,
                USER_AUTH_TOKENS.PURPOSE.eq(AuthTokenPurpose.PASSWORD_RESET.value())))
        .isZero();
    verifyNoInteractions(mailSender);
  }

  @Test
  void forgotPasswordConsumesExistingResetTokensCreatesNewTokenAndSendsEmail() throws Exception {
    User user = createUnverifiedUser("forgot@example.com");
    authTokenService.createPasswordResetToken(user.id());

    mockMvc
        .perform(
            post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotPasswordJson("forgot@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    assertThat(countPasswordResetTokens(user.id())).isEqualTo(2);
    assertThat(countActivePasswordResetTokens(user.id())).isEqualTo(1);

    String tokenHash =
        dsl.select(USER_AUTH_TOKENS.TOKEN_HASH)
            .from(USER_AUTH_TOKENS)
            .where(USER_AUTH_TOKENS.USER_ID.eq(user.id()))
            .and(USER_AUTH_TOKENS.PURPOSE.eq(AuthTokenPurpose.PASSWORD_RESET.value()))
            .and(USER_AUTH_TOKENS.CONSUMED_AT.isNull())
            .fetchSingle(USER_AUTH_TOKENS.TOKEN_HASH);
    assertThat(tokenHash).hasSize(64);

    SimpleMailMessage message = captureOnlyMessage();
    assertThat(message.getTo()).containsExactly("forgot@example.com");
    assertThat(message.getText()).contains("/reset-password?token=");
  }

  @Test
  void forgotPasswordReturnsOkWhenEmailSendingFails() throws Exception {
    User user = createUnverifiedUser("forgot-send-failure@example.com");
    doThrow(new MailSendException("boom")).when(mailSender).send(any(SimpleMailMessage.class));

    mockMvc
        .perform(
            post("/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(forgotPasswordJson("forgot-send-failure@example.com")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    assertThat(countPasswordResetTokens(user.id())).isEqualTo(1);
    verify(mailSender).send(any(SimpleMailMessage.class));
  }

  @Test
  void resetPasswordRejectsInvalidExpiredConsumedAndWrongPurposeTokens() throws Exception {
    expectInvalidPasswordResetToken("not-a-valid-token");

    User expiredUser = createVerifiedUser("reset-expired@example.com");
    String expiredToken = authTokenService.createPasswordResetToken(expiredUser.id());
    dsl.update(USER_AUTH_TOKENS)
        .set(USER_AUTH_TOKENS.EXPIRES_AT, OffsetDateTime.now().minusMinutes(1))
        .where(USER_AUTH_TOKENS.USER_ID.eq(expiredUser.id()))
        .and(USER_AUTH_TOKENS.PURPOSE.eq(AuthTokenPurpose.PASSWORD_RESET.value()))
        .execute();
    expectInvalidPasswordResetToken(expiredToken);

    User consumedUser = createVerifiedUser("reset-consumed@example.com");
    String consumedToken = authTokenService.createPasswordResetToken(consumedUser.id());
    resetPasswordViaApi(consumedToken, NEW_PASSWORD);
    expectInvalidPasswordResetToken(consumedToken);

    User wrongPurposeUser = createUnverifiedUser("reset-wrong-purpose@example.com");
    String emailVerificationToken =
        authTokenService.createEmailVerificationToken(wrongPurposeUser.id());
    expectInvalidPasswordResetToken(emailVerificationToken);
  }

  @Test
  void resetPasswordUpdatesHashConsumesTokensAndAllowsNewPasswordLogin() throws Exception {
    User user = createVerifiedUser("reset-success@example.com");
    String token = authTokenService.createPasswordResetToken(user.id());
    insertActivePasswordResetToken(user.id());

    resetPasswordViaApi(token, NEW_PASSWORD);

    User updatedUser = userRepository.findByEmail("reset-success@example.com").orElseThrow();
    assertThat(passwordService.matches(PASSWORD, updatedUser.passwordHash())).isFalse();
    assertThat(passwordService.matches(NEW_PASSWORD, updatedUser.passwordHash())).isTrue();
    assertThat(countActivePasswordResetTokens(user.id())).isZero();

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("reset-success@example.com", PASSWORD)))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("reset-success@example.com", NEW_PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value("reset-success@example.com"))
        .andExpect(cookie().exists(CookieService.ACCESS_TOKEN_COOKIE_NAME));
  }

  @Test
  void resetPasswordSetsHashForUserWithoutPasswordHashAndAllowsNewPasswordLogin() throws Exception {
    User user = createVerifiedUser("reset-null-hash@example.com");
    setPasswordHash(user.id(), null);
    String token = authTokenService.createPasswordResetToken(user.id());

    resetPasswordViaApi(token, NEW_PASSWORD);

    User updatedUser = userRepository.findByEmail("reset-null-hash@example.com").orElseThrow();
    assertThat(updatedUser.emailVerifiedAt()).isNotNull();
    assertThat(updatedUser.passwordHash()).isNotNull();
    assertThat(passwordService.matches(NEW_PASSWORD, updatedUser.passwordHash())).isTrue();

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("reset-null-hash@example.com", NEW_PASSWORD)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value("reset-null-hash@example.com"))
        .andExpect(cookie().exists(CookieService.ACCESS_TOKEN_COOKIE_NAME));
  }

  @Test
  void resetPasswordDoesNotVerifyEmail() throws Exception {
    User user = createUnverifiedUser("reset-unverified@example.com");
    String token = authTokenService.createPasswordResetToken(user.id());

    resetPasswordViaApi(token, NEW_PASSWORD);

    User updatedUser = userRepository.findByEmail("reset-unverified@example.com").orElseThrow();
    assertThat(updatedUser.emailVerifiedAt()).isNull();
    assertThat(passwordService.matches(NEW_PASSWORD, updatedUser.passwordHash())).isTrue();

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("reset-unverified@example.com", NEW_PASSWORD)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
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

  private void expectInvalidPasswordResetToken(String token) throws Exception {
    mockMvc
        .perform(
            post("/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(passwordResetJson(token, NEW_PASSWORD)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_AUTH_TOKEN"));
  }

  private void resetPasswordViaApi(String token, String newPassword) throws Exception {
    mockMvc
        .perform(
            post("/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(passwordResetJson(token, newPassword)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));
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

  private List<SimpleMailMessage> captureMessages(int count) {
    ArgumentCaptor<SimpleMailMessage> messageCaptor =
        ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender, times(count)).send(messageCaptor.capture());
    return messageCaptor.getAllValues();
  }

  private String extractToken(SimpleMailMessage message) {
    var matcher = EMAIL_TOKEN_PATTERN.matcher(message.getText());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private User createUnverifiedUser(String email) {
    return userRepository.createUser(email, passwordService.hash(PASSWORD), "Test User");
  }

  private User createVerifiedUser(String email) {
    User user = createUnverifiedUser(email);
    userRepository.markEmailVerified(user.id(), OffsetDateTime.now());
    return user;
  }

  private void setPasswordHash(UUID userId, String passwordHash) {
    dsl.update(USERS).set(USERS.PASSWORD_HASH, passwordHash).where(USERS.ID.eq(userId)).execute();
  }

  private int countPasswordResetTokens(UUID userId) {
    return dsl.fetchCount(
        USER_AUTH_TOKENS,
        USER_AUTH_TOKENS.USER_ID.eq(userId),
        USER_AUTH_TOKENS.PURPOSE.eq(AuthTokenPurpose.PASSWORD_RESET.value()));
  }

  private int countActivePasswordResetTokens(UUID userId) {
    return dsl.fetchCount(
        USER_AUTH_TOKENS,
        USER_AUTH_TOKENS.USER_ID.eq(userId),
        USER_AUTH_TOKENS.PURPOSE.eq(AuthTokenPurpose.PASSWORD_RESET.value()),
        USER_AUTH_TOKENS.CONSUMED_AT.isNull());
  }

  private void insertActivePasswordResetToken(UUID userId) {
    OffsetDateTime now = OffsetDateTime.now();

    dsl.insertInto(USER_AUTH_TOKENS)
        .set(USER_AUTH_TOKENS.ID, UUID.randomUUID())
        .set(USER_AUTH_TOKENS.USER_ID, userId)
        .set(USER_AUTH_TOKENS.PURPOSE, AuthTokenPurpose.PASSWORD_RESET.value())
        .set(USER_AUTH_TOKENS.TOKEN_HASH, "a".repeat(64))
        .set(USER_AUTH_TOKENS.EXPIRES_AT, now.plusHours(1))
        .set(USER_AUTH_TOKENS.CREATED_AT, now)
        .execute();
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
    return loginJson(email, PASSWORD);
  }

  private static String loginJson(String email, String password) {
    return """
        {
          "email": "%s",
          "password": "%s"
        }
        """
        .formatted(email, password);
  }

  private static String forgotPasswordJson(String email) {
    return """
        {
          "email": "%s"
        }
        """
        .formatted(email);
  }

  private static String passwordResetJson(String token, String newPassword) {
    return """
        {
          "token": "%s",
          "newPassword": "%s"
        }
        """
        .formatted(token, newPassword);
  }

  private record RegistrationResult(UUID userId, String token) {}
}
