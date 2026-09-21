package com.offertrack.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertrack.errors.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class AuthenticationErrorWriter {
  private AuthenticationErrorWriter() {}

  public static void write(
      ObjectMapper mapper,
      HttpServletRequest request,
      HttpServletResponse response,
      boolean unavailable)
      throws IOException {
    int status = unavailable ? 503 : 401;
    response.setStatus(status);
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-store");
    mapper.writeValue(
        response.getOutputStream(),
        ApiErrorResponse.of(
            status,
            unavailable ? "AUTH_SERVICE_UNAVAILABLE" : "AUTHENTICATION_REQUIRED",
            unavailable
                ? "Authentication service is temporarily unavailable."
                : "Authentication required.",
            request.getRequestURI()));
  }
}
