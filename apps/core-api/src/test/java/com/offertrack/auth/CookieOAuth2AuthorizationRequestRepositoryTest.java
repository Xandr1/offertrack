package com.offertrack.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class CookieOAuth2AuthorizationRequestRepositoryTest {
  private static final String SIGNING_SECRET = "test-oauth-cookie-secret";

  private final CookieOAuth2AuthorizationRequestRepository repository =
      new CookieOAuth2AuthorizationRequestRepository(SIGNING_SECRET);

  @Test
  void saveWritesSignedCookieWithExpectedAttributes() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    repository.saveAuthorizationRequest(
        authorizationRequest(), new MockHttpServletRequest(), response);

    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    String cookieValue = extractCookieValue(setCookie);
    String[] segments = cookieValue.split("\\.", -1);

    assertThat(setCookie)
        .contains(
            CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME + "=",
            "Max-Age=180",
            "Path=/",
            "HttpOnly",
            "SameSite=None",
            "Secure");
    assertThat(segments).hasSize(2);
    assertThat(segments[0]).matches("[A-Za-z0-9_-]+");
    assertThat(segments[1]).isEqualTo(sign(segments[0]));
  }

  @Test
  void loadReturnsSavedAuthorizationRequest() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    OAuth2AuthorizationRequest authorizationRequest = authorizationRequest();

    repository.saveAuthorizationRequest(
        authorizationRequest, new MockHttpServletRequest(), response);

    OAuth2AuthorizationRequest loaded =
        repository.loadAuthorizationRequest(requestWithCookie(extractCookieValue(response)));

    assertThat(loaded).isNotNull();
    assertThat(loaded.getAuthorizationUri()).isEqualTo(authorizationRequest.getAuthorizationUri());
    assertThat(loaded.getClientId()).isEqualTo(authorizationRequest.getClientId());
    assertThat(loaded.getRedirectUri()).isEqualTo(authorizationRequest.getRedirectUri());
    assertThat(loaded.getState()).isEqualTo(authorizationRequest.getState());
    assertThat(loaded.getScopes())
        .containsExactlyInAnyOrderElementsOf(authorizationRequest.getScopes());
  }

  @Test
  void removeReturnsSavedAuthorizationRequestAndClearsCookie() {
    MockHttpServletResponse saveResponse = new MockHttpServletResponse();

    repository.saveAuthorizationRequest(
        authorizationRequest(), new MockHttpServletRequest(), saveResponse);

    MockHttpServletResponse removeResponse = new MockHttpServletResponse();
    OAuth2AuthorizationRequest removed =
        repository.removeAuthorizationRequest(
            requestWithCookie(extractCookieValue(saveResponse)), removeResponse);

    assertThat(removed).isNotNull();
    assertThat(removed.getState()).isEqualTo("state-123");
    assertThat(removeResponse.getHeader(HttpHeaders.SET_COOKIE))
        .contains(
            CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME + "=",
            "Max-Age=0",
            "Path=/",
            "HttpOnly",
            "SameSite=None",
            "Secure");
  }

  @Test
  void saveNullClearsCookie() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    repository.saveAuthorizationRequest(null, new MockHttpServletRequest(), response);

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
        .contains(
            CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME + "=",
            "Max-Age=0");
  }

  @Test
  void loadReturnsNullWhenCookieIsMissing() {
    assertThat(repository.loadAuthorizationRequest(new MockHttpServletRequest())).isNull();
  }

  @Test
  void loadReturnsNullForMalformedUnsignedAndSignatureMismatchedCookies() {
    assertInvalidCookieReturnsNull("unsigned-payload");
    assertInvalidCookieReturnsNull("payload.");
    assertInvalidCookieReturnsNull(".signature");
    assertInvalidCookieReturnsNull("payload.signature.extra");
    assertInvalidCookieReturnsNull("payload.not-base64!");
    assertInvalidCookieReturnsNull("payload." + sign("different-payload"));
  }

  @Test
  void loadReturnsNullForInvalidBase64PayloadWithValidSignature() {
    String payload = "not-base64!";

    assertInvalidCookieReturnsNull(payload + "." + sign(payload));
  }

  @Test
  void loadReturnsNullForCorruptSerializedPayloadWithValidSignature() {
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                "not a serialized authorization request".getBytes(StandardCharsets.UTF_8));

    assertInvalidCookieReturnsNull(payload + "." + sign(payload));
  }

  private void assertInvalidCookieReturnsNull(String cookieValue) {
    assertThat(repository.loadAuthorizationRequest(requestWithCookie(cookieValue))).isNull();
  }

  private static OAuth2AuthorizationRequest authorizationRequest() {
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
        .clientId("google-client-id")
        .redirectUri("http://localhost:8080/login/oauth2/code/google")
        .scopes(Set.of("openid", "email", "profile"))
        .state("state-123")
        .additionalParameters(Map.of("prompt", "select_account"))
        .attributes(Map.of("registration_id", "google"))
        .build();
  }

  private static MockHttpServletRequest requestWithCookie(String cookieValue) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie(
            CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME,
            cookieValue));
    return request;
  }

  private static String extractCookieValue(MockHttpServletResponse response) {
    return extractCookieValue(response.getHeader(HttpHeaders.SET_COOKIE));
  }

  private static String extractCookieValue(String setCookie) {
    String prefix =
        CookieOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST_COOKIE_NAME + "=";
    int start = setCookie.indexOf(prefix) + prefix.length();
    int end = setCookie.indexOf(';', start);
    return setCookie.substring(start, end);
  }

  private static String sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
