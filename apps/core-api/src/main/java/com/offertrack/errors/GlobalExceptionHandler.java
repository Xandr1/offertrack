package com.offertrack.errors;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
  private static final String INTERNAL_ERROR_MESSAGE =
      "Something went wrong on the server. Try again.";

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiErrorResponse> handleDomainException(
      DomainException exception, HttpServletRequest request) {
    HttpStatus status = mapDomainStatus(exception.code());

    if (status.is5xxServerError()) {
      logUnexpected5xx(exception, request);
      return buildResponse(status, INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE, request);
    }

    String code = exception.code();
    String message = exception.getMessage();

    logExpected4xx(status, code, message, request);
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

    logExpected4xx(status, code, message, request);
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

    logExpected4xx(status, code, message, request);
    return buildResponse(status, code, message, request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiErrorResponse> handleInvalidRequestParameter(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    String code = "INVALID_REQUEST";
    String message = "Invalid request parameter.";

    logExpected4xx(status, code, message, request);
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

    logExpected4xx(status, code, message, request);
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
      logExpected4xx(status, code, message, request);
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
      case "INVALID_INTERVIEW_COUNT", "DUPLICATE_INTERVIEW_IDS", "INVALID_AUTH_TOKEN" ->
          HttpStatus.BAD_REQUEST;
      default -> HttpStatus.INTERNAL_SERVER_ERROR;
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

  private static void logExpected4xx(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    log.warn(
        "{} {} -> {} {}: {}",
        request.getMethod(),
        request.getRequestURI(),
        status.value(),
        code,
        message);
  }

  private static void logUnexpected5xx(Exception exception, HttpServletRequest request) {
    log.error(
        "{} {} -> 500 INTERNAL_ERROR", request.getMethod(), request.getRequestURI(), exception);
  }
}
