package com.offertrack.errors;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

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

  private static MockHttpServletRequest sensitiveRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/applications");
    request.setQueryString("token=raw-url-token-marker");
    request.addHeader("Authorization", "Bearer authorization-header-marker");
    request.setCookies(new Cookie("access_token", "cookie-marker"));
    request.setContent("{\"token\":\"jwt-marker-secret\"}".getBytes());
    return request;
  }
}
