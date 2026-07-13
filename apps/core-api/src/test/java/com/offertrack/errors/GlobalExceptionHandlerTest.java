package com.offertrack.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.offertrack.config.RequestIdFilter;
import com.offertrack.ratelimit.RateLimitExceededException;
import com.offertrack.ratelimit.RateLimitPolicy;
import com.offertrack.ratelimit.RateLimitServiceUnavailableException;
import com.offertrack.ratelimit.RateLimitSubjectType;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {
  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void fourHundredLogUsesOnlySafeRequestAndErrorFields(CapturedOutput output) {
    MockHttpServletRequest request = sensitiveRequest();
    DomainException exception =
        new DomainException("INVALID_AUTH_TOKEN", "client-facing-message-marker") {};

    ResponseEntity<ApiErrorResponse> response = handler.handleDomainException(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("client-facing-message-marker");
    assertThat(response.getBody().code()).isEqualTo("INVALID_AUTH_TOKEN");
    assertThat(output.getOut())
        .contains(
            "http_request_rejected method=POST path=/api/applications status=400 error_code=INVALID_AUTH_TOKEN")
        .doesNotContain(
            "client-facing-message-marker",
            "raw-url-token-marker",
            "authorization-header-marker",
            "cookie-marker",
            "jwt-marker-secret");
  }

  @Test
  void fiveHundredLogOmitsExceptionMessageAndStackTrace(CapturedOutput output) {
    MockHttpServletRequest request = sensitiveRequest();
    IllegalStateException exception =
        new IllegalStateException("provider-message-marker jwt-marker-secret");

    ResponseEntity<ApiErrorResponse> response =
        handler.handleUnexpectedException(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.getBody().message())
        .isEqualTo("Something went wrong on the server. Try again.");
    assertThat(output.getOut())
        .contains(
            "http_request_failed method=POST path=/api/applications status=500 error_code=INTERNAL_ERROR error_type=IllegalStateException")
        .doesNotContain(
            "provider-message-marker",
            "jwt-marker-secret",
            "raw-url-token-marker",
            "authorization-header-marker",
            "cookie-marker",
            "at com.offertrack");
  }

  @Test
  void rateLimitResponseIncludesStableBodyAndPositiveRetryAfter(CapturedOutput output) {
    MockHttpServletRequest request = sensitiveRequest();

    ResponseEntity<ApiErrorResponse> response =
        handler.handleRateLimitExceeded(rateLimitException(), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("37");
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("RATE_LIMITED");
    assertThat(response.getBody().message()).isEqualTo("Too many requests. Try again later.");
    assertThat(output.getOut())
        .contains(
            "rate_limit_denied method=POST route=/api/applications status=429 error_code=RATE_LIMITED",
            "denied_policies=[login-email, login-ip]",
            "subject_types=[email, ip]",
            "retry_after=37",
            "request_id=test-correlation-id")
        .doesNotContain(
            "raw-url-token-marker",
            "authorization-header-marker",
            "cookie-marker",
            "jwt-marker-secret",
            "raw-email-marker@example.com",
            "203.0.113.99",
            "11111111-1111-1111-1111-111111111111");
    assertThat(StringUtils.countOccurrencesOf(output.getOut(), "rate_limit_denied")).isEqualTo(1);
    assertThat(output.getOut()).doesNotContain("http_request_rejected");
  }

  @Test
  void rateLimitLogsUseTheFixedHandlerPatternInsteadOfTheRawRequestUri(CapturedOutput output) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/auth/login;password=raw-path-marker");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/auth/login");

    request.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, "test-correlation-id");

    handler.handleRateLimitExceeded(rateLimitException(), request);

    assertThat(output.getOut()).contains("route=/auth/login").doesNotContain("raw-path-marker");
  }

  @Test
  void unavailableLimiterReturnsStableSafeResponse(CapturedOutput output) {
    MockHttpServletRequest request = sensitiveRequest();

    ResponseEntity<ApiErrorResponse> response =
        handler.handleRateLimitUnavailable(new RateLimitServiceUnavailableException(), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("RATE_LIMIT_SERVICE_UNAVAILABLE");
    assertThat(response.getBody().message())
        .isEqualTo("Security service is temporarily unavailable.");
    assertThat(output.getOut())
        .doesNotContain(
            "raw-url-token-marker",
            "authorization-header-marker",
            "cookie-marker",
            "jwt-marker-secret");
  }

  private static MockHttpServletRequest sensitiveRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/applications");
    request.setQueryString(
        "token=raw-url-token-marker&email=raw-email-marker@example.com&ip=203.0.113.99&user=11111111-1111-1111-1111-111111111111");
    request.addHeader("Authorization", "Bearer authorization-header-marker");
    request.setCookies(new Cookie("access_token", "cookie-marker"));
    request.setContent("{\"token\":\"jwt-marker-secret\"}".getBytes());
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/applications");
    request.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, "test-correlation-id");
    return request;
  }

  private static RateLimitExceededException rateLimitException() {
    return new RateLimitExceededException(
        List.of(RateLimitPolicy.LOGIN_EMAIL, RateLimitPolicy.LOGIN_IP),
        List.of(RateLimitSubjectType.EMAIL, RateLimitSubjectType.IP),
        37);
  }
}
