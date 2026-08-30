package com.offertrack.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class DependencyHealthAuthenticationFilter extends OncePerRequestFilter {
  private static final String PATH = "/actuator/health/dependencies";
  private static final String HEADER = "X-Internal-Api-Key";

  private final String expectedKey;

  DependencyHealthAuthenticationFilter(Environment environment) {
    this.expectedKey = environment.getProperty("app.ai-service.internal-api-key", "");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (PATH.equals(request.getRequestURI())) {
      String suppliedKey = request.getHeader(HEADER);
      if (suppliedKey != null
          && MessageDigest.isEqual(
              suppliedKey.getBytes(StandardCharsets.UTF_8),
              expectedKey.getBytes(StandardCharsets.UTF_8))) {
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "dependency-health", null, AuthorityUtils.NO_AUTHORITIES));
      }
    }
    filterChain.doFilter(request, response);
  }
}
