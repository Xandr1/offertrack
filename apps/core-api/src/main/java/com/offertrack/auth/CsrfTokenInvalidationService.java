package com.offertrack.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfTokenRepository;

public class CsrfTokenInvalidationService {
  private final CsrfTokenRepository csrfTokenRepository;

  public CsrfTokenInvalidationService(CsrfTokenRepository csrfTokenRepository) {
    this.csrfTokenRepository = csrfTokenRepository;
  }

  public void invalidate(HttpServletRequest request, HttpServletResponse response) {
    csrfTokenRepository.saveToken(null, request, response);
  }
}
