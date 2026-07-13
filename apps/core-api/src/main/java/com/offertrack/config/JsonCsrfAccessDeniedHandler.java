package com.offertrack.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertrack.errors.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

public final class JsonCsrfAccessDeniedHandler implements AccessDeniedHandler {
  public static final String ERROR_CODE = "CSRF_INVALID";
  private static final String ERROR_MESSAGE = "CSRF token is missing or invalid.";

  private final ObjectMapper objectMapper;

  public JsonCsrfAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    if (!(accessDeniedException instanceof CsrfException)) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      return;
    }

    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(),
        ApiErrorResponse.of(
            HttpStatus.FORBIDDEN.value(), ERROR_CODE, ERROR_MESSAGE, request.getRequestURI()));
  }
}
