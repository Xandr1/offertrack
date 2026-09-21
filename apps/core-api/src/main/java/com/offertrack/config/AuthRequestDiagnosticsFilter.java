package com.offertrack.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Suppress credential-bearing framework DEBUG/TRACE events only during auth requests. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthRequestDiagnosticsFilter extends OncePerRequestFilter {
  static final String SENSITIVE = "auth_sensitive_request";
  private static final org.slf4j.Logger log =
      LoggerFactory.getLogger(AuthRequestDiagnosticsFilter.class);
  private final TurboFilter privacy =
      new TurboFilter() {
        @Override
        public FilterReply decide(
            Marker marker,
            Logger logger,
            Level level,
            String format,
            Object[] arguments,
            Throwable error) {
          String name = logger.getName();
          boolean framework =
              name.startsWith("org.springframework.web.")
                  || name.startsWith("org.springframework.security.")
                  || name.startsWith("org.springframework.jdbc.")
                  || name.startsWith("org.postgresql.");
          return "true".equals(MDC.get(SENSITIVE)) && framework && level.toInt() < Level.INFO_INT
              ? FilterReply.DENY
              : FilterReply.NEUTRAL;
        }
      };

  @PostConstruct
  void install() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    privacy.setContext(context);
    privacy.start();
    context.addTurboFilter(privacy);
  }

  @PreDestroy
  void uninstall() {
    ((LoggerContext) LoggerFactory.getILoggerFactory()).getTurboFilterList().remove(privacy);
    privacy.stop();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !(path.startsWith("/auth/")
        || path.startsWith("/oauth2/")
        || path.startsWith("/login/oauth2/")
        || path.equals("/api/me"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    MDC.put(SENSITIVE, "true");
    long started = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      // Never log the URI/query, headers, principal, body or exception message.
      String operation =
          request.getRequestURI().equals("/login/oauth2/code/google")
              ? "google_callback"
              : "auth_request";
      log.info(
          "auth_request_completed operation={} status={} duration_ms={} request_id={}",
          operation,
          response.getStatus(),
          (System.nanoTime() - started) / 1_000_000,
          RequestIdFilter.requestId(request));
      MDC.remove(SENSITIVE);
    }
  }
}
