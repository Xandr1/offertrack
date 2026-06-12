package com.offertrack.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.StringUtils;

public class CookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
  public static final String AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_authorization_request";

  private static final Duration AUTHORIZATION_REQUEST_COOKIE_TTL = Duration.ofSeconds(180);
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

  private final SecretKeySpec signingKey;

  public CookieOAuth2AuthorizationRequestRepository(String signingSecret) {
    if (!StringUtils.hasText(signingSecret)) {
      throw new IllegalArgumentException(
          "OAuth authorization request cookie signing secret must not be blank");
    }

    this.signingKey =
        new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
  }

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    return findCookieValue(request).flatMap(this::decodeAndVerifyAuthorizationRequest).orElse(null);
  }

  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (authorizationRequest == null) {
      clearAuthorizationRequestCookie(response);
      return;
    }

    String payload = BASE64_URL_ENCODER.encodeToString(serialize(authorizationRequest));
    String signature = sign(payload);
    addCookie(response, payload + "." + signature, AUTHORIZATION_REQUEST_COOKIE_TTL);
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
    clearAuthorizationRequestCookie(response);
    return authorizationRequest;
  }

  public void clearAuthorizationRequestCookie(HttpServletResponse response) {
    addCookie(response, "", Duration.ZERO);
  }

  private Optional<String> findCookieValue(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();

    if (cookies == null) {
      return Optional.empty();
    }

    return Arrays.stream(cookies)
        .filter(cookie -> AUTHORIZATION_REQUEST_COOKIE_NAME.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst();
  }

  private Optional<OAuth2AuthorizationRequest> decodeAndVerifyAuthorizationRequest(
      String cookieValue) {
    if (!StringUtils.hasText(cookieValue)) {
      return Optional.empty();
    }

    String[] segments = cookieValue.split("\\.", -1);

    if (segments.length != 2
        || !StringUtils.hasText(segments[0])
        || !StringUtils.hasText(segments[1])) {
      return Optional.empty();
    }

    String payload = segments[0];
    String signature = segments[1];

    if (!isValidSignature(payload, signature)) {
      return Optional.empty();
    }

    try {
      byte[] serializedAuthorizationRequest = BASE64_URL_DECODER.decode(payload);
      Object deserialized = deserialize(serializedAuthorizationRequest);

      if (deserialized instanceof OAuth2AuthorizationRequest authorizationRequest) {
        return Optional.of(authorizationRequest);
      }
    } catch (RuntimeException | IOException | ClassNotFoundException exception) {
      return Optional.empty();
    }

    return Optional.empty();
  }

  private boolean isValidSignature(String payload, String signature) {
    byte[] providedSignature;

    try {
      providedSignature = BASE64_URL_DECODER.decode(signature);
    } catch (IllegalArgumentException exception) {
      return false;
    }

    return MessageDigest.isEqual(providedSignature, hmac(payload));
  }

  private String sign(String payload) {
    return BASE64_URL_ENCODER.encodeToString(hmac(payload));
  }

  private byte[] hmac(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(signingKey);
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "Could not sign OAuth authorization request cookie", exception);
    }
  }

  private static byte[] serialize(OAuth2AuthorizationRequest authorizationRequest) {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(authorizationRequest);
      return bytes.toByteArray();
    } catch (IOException exception) {
      throw new IllegalArgumentException(
          "Could not serialize OAuth authorization request", exception);
    }
  }

  private static Object deserialize(byte[] serializedAuthorizationRequest)
      throws IOException, ClassNotFoundException {
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(serializedAuthorizationRequest))) {
      return input.readObject();
    }
  }

  private static void addCookie(HttpServletResponse response, String value, Duration maxAge) {
    String cookie =
        AUTHORIZATION_REQUEST_COOKIE_NAME
            + "="
            + value
            + "; Max-Age="
            + maxAge.toSeconds()
            + "; Path=/"
            + "; HttpOnly"
            + "; SameSite=None"
            + "; Secure";

    response.addHeader(HttpHeaders.SET_COOKIE, cookie);
  }
}
