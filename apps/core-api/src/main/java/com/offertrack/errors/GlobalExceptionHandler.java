package com.offertrack.errors;

import com.offertrack.config.RequestIdFilter;
import com.offertrack.ratelimit.RateLimitExceededException;
import com.offertrack.ratelimit.RateLimitServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
  private static final String INTERNAL_ERROR_MESSAGE =
      "Something went wrong on the server. Try again.";

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(
      RateLimitExceededException exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
    log.warn(
        "rate_limit_denied method={} route={} status={} error_code={} denied_policies={} subject_types={} retry_after={} request_id={}",
        request.getMethod(),
        safeLogPath(request),
        status.value(),
        "RATE_LIMITED",
        exception.deniedPolicies().stream().map(policy -> policy.key()).toList(),
        exception.subjectTypes().stream().map(subjectType -> subjectType.key()).toList(),
        exception.retryAfterSeconds(),
        RequestIdFilter.requestId(request));

    return ResponseEntity.status(status)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
        .body(
            ApiErrorResponse.of(
                status.value(),
                "RATE_LIMITED",
                "Too many requests. Try again later.",
                request.getRequestURI()));
  }

  @ExceptionHandler(RateLimitServiceUnavailableException.class)
  public ResponseEntity<ApiErrorResponse> handleRateLimitUnavailable(
      RateLimitServiceUnavailableException exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
    return buildResponse(
        status,
        "RATE_LIMIT_SERVICE_UNAVAILABLE",
        "Security service is temporarily unavailable.",
        request);
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiErrorResponse> handleDomainException(
      DomainException exception, HttpServletRequest request) {
    HttpStatus status = mapDomainStatus(exception.code());

    String code = exception.code();
    String message = exception.getMessage();

    if (status.is5xxServerError()) {
      logUnexpected5xx(exception, request, status, code);

      if (isSafeDependencyError(code)) {
        return buildResponse(status, code, message, request);
      }

      return buildResponse(status, INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE, request);
    }

    logExpected4xx(status, code, request);
    return buildResponse(status, code, message, request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidationException(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    String code = "VALIDATION_ERROR";
    String message = "Please check the request fields.";
    List<ApiErrorResponse.FieldError> fieldErrors =
        exception.getBindingResult().getFieldErrors().stream().map(this::toFieldError).toList();

    logExpected4xx(status, code, request);
    return ResponseEntity.status(status)
        .body(
            ApiErrorResponse.withFieldErrors(
                status.value(), code, message, request.getRequestURI(), fieldErrors));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    String code = "MALFORMED_REQUEST";
    String message = "Malformed request body.";

    logExpected4xx(status, code, request);
    return buildResponse(status, code, message, request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidRequestParameter(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    String code = "INVALID_REQUEST";
    String message = "Invalid request parameter.";

    logExpected4xx(status, code, request);
    return buildResponse(status, code, message, request);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
      ResponseStatusException exception, HttpServletRequest request) {
    int statusCode = exception.getStatusCode().value();
    HttpStatus status = HttpStatus.valueOf(statusCode);

    if (status.is5xxServerError()) {
      logUnexpected5xx(exception, request);
      return buildResponse(status, INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE, request);
    }

    String code = "REQUEST_FAILED";
    String message =
        exception.getReason() == null || exception.getReason().isBlank()
            ? "Request failed."
            : exception.getReason();

    logExpected4xx(status, code, request);
    return buildResponse(status, code, message, request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
      Exception exception, HttpServletRequest request) {
    if (exception instanceof ErrorResponse errorResponse) {
      HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());

      if (status == null || status.is5xxServerError()) {
        logUnexpected5xx(exception, request);
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE, request);
      }

      String code = "REQUEST_FAILED";
      String message = "Request failed.";
      logExpected4xx(status, code, request);
      return buildResponse(status, code, message, request);
    }

    logUnexpected5xx(exception, request);
    return buildResponse(
        HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE, request);
  }

  private static HttpStatus mapDomainStatus(String code) {
    return switch (code) {
      case "APPLICATION_NOT_FOUND", "INTERVIEW_NOT_FOUND" -> HttpStatus.NOT_FOUND;
      case "EMAIL_NOT_VERIFIED" -> HttpStatus.FORBIDDEN;
      case "INVALID_INTERVIEW_COUNT",
              "DUPLICATE_INTERVIEW_IDS",
              "INVALID_AUTH_TOKEN",
              "AI_SERVICE_INVALID_URL" ->
          HttpStatus.BAD_REQUEST;
      case "AI_SERVICE_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
      case "AI_SERVICE_UNAVAILABLE", "AI_SERVICE_FETCH_FAILED", "AI_SERVICE_EXTRACTION_FAILED" ->
          HttpStatus.BAD_GATEWAY;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }

  private static boolean isSafeDependencyError(String code) {
    return switch (code) {
      case "AI_SERVICE_TIMEOUT",
              "AI_SERVICE_UNAVAILABLE",
              "AI_SERVICE_FETCH_FAILED",
              "AI_SERVICE_EXTRACTION_FAILED" ->
          true;
      default -> false;
    };
  }

  private static ResponseEntity<ApiErrorResponse> buildResponse(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.of(status.value(), code, message, request.getRequestURI()));
  }

  private ApiErrorResponse.FieldError toFieldError(FieldError fieldError) {
    String message =
        fieldError.getDefaultMessage() == null
            ? "Invalid field value."
            : fieldError.getDefaultMessage();
    return new ApiErrorResponse.FieldError(fieldError.getField(), message);
  }

  private static void logExpected4xx(HttpStatus status, String code, HttpServletRequest request) {
    log.warn(
        "http_request_rejected method={} path={} status={} error_code={}",
        request.getMethod(),
        safeLogPath(request),
        status.value(),
        code);
  }

  private static void logUnexpected5xx(Exception exception, HttpServletRequest request) {
    logUnexpected5xx(exception, request, HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_CODE);
  }

  private static void logUnexpected5xx(
      Exception exception, HttpServletRequest request, HttpStatus status, String code) {
    log.error(
        "http_request_failed method={} path={} status={} error_code={} error_type={}",
        request.getMethod(),
        safeLogPath(request),
        status.value(),
        code,
        exception.getClass().getSimpleName());
  }

  private static String safeLogPath(HttpServletRequest request) {
    Object matchingPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return matchingPattern == null ? "unmatched" : matchingPattern.toString();
  }
}
