package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRequestDiagnosticsFilterTest {
  @Test
  void sensitiveVerboseEventsAreExcludedWhileOrdinaryRequestsAndSafeOutcomesRemain()
      throws Exception {
    var filter = new AuthRequestDiagnosticsFilter();
    Logger framework =
        (Logger) LoggerFactory.getLogger("org.springframework.web.auth-diagnostic-test");
    Logger application = (Logger) LoggerFactory.getLogger(AuthRequestDiagnosticsFilter.class);
    var messages = new ListAppender<ILoggingEvent>();
    messages.start();
    framework.addAppender(messages);
    application.addAppender(messages);
    Level previous = framework.getLevel();
    framework.setLevel(Level.DEBUG);
    filter.install();
    try {
      var request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
      request.setQueryString("code=private-code&state=private-state");
      var response = new MockHttpServletResponse();
      filter.doFilter(
          request,
          response,
          (incoming, outgoing) -> {
            framework.debug("credentials={}", "private-code");
            response.setStatus(302);
          });
      framework.debug("ordinary_request_diagnostic");
      String output =
          messages.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .reduce("", (left, right) -> left + right);
      assertThat(output)
          .contains(
              "auth_request_completed",
              "google_callback",
              "status=302",
              "request_id=",
              "ordinary_request_diagnostic")
          .doesNotContain("private-code", "private-state", "credentials=");
    } finally {
      filter.uninstall();
      framework.setLevel(previous);
      framework.detachAppender(messages);
      application.detachAppender(messages);
    }
  }
}
