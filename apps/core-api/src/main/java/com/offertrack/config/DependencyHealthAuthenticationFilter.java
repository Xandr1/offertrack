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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

final class DependencyHealthAuthenticationFilter extends OncePerRequestFilter {
  static final String PATH = "/actuator/health/dependencies";
  static final String HEADER = "X-Dependency-Health-Key";
  static final String AUTHORITY = "DEPENDENCY_HEALTH";

  private final String expectedKey;

  DependencyHealthAuthenticationFilter(Environment environment) {
    this.expectedKey = environment.getProperty("app.management.dependency-health-key", "");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (PATH.equals(request.getRequestURI())) {
      String suppliedKey = request.getHeader(HEADER);
      if (StringUtils.hasText(expectedKey)
          && StringUtils.hasText(suppliedKey)
          && MessageDigest.isEqual(
              suppliedKey.getBytes(StandardCharsets.UTF_8),
              expectedKey.getBytes(StandardCharsets.UTF_8))) {
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "dependency-health", null, AuthorityUtils.createAuthorityList(AUTHORITY)));
      }
    }
    filterChain.doFilter(request, response);
  }
}
