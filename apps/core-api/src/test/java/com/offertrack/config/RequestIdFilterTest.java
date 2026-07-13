package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
  private final RequestIdFilter filter = new RequestIdFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void acceptsOneStrictlyValidatedClientRequestIdAndClearsMdc() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
    request.addHeader(RequestIdFilter.HEADER_NAME, "client-request_123:abc.def");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringRequest = new AtomicReference<>();
    FilterChain chain =
        (servletRequest, servletResponse) -> mdcDuringRequest.set(MDC.get(RequestIdFilter.MDC_KEY));

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader(RequestIdFilter.HEADER_NAME))
        .isEqualTo("client-request_123:abc.def");
    assertThat(request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE))
        .isEqualTo("client-request_123:abc.def");
    assertThat(mdcDuringRequest).hasValue("client-request_123:abc.def");
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void replacesMissingInvalidOverlongControlAndMultipleValuesWithUuid() throws Exception {
    assertGeneratedUuid(new MockHttpServletRequest("GET", "/missing"));

    MockHttpServletRequest invalid = new MockHttpServletRequest("GET", "/invalid");
    invalid.addHeader(RequestIdFilter.HEADER_NAME, "contains spaces");
    assertGeneratedUuid(invalid);

    MockHttpServletRequest overlong = new MockHttpServletRequest("GET", "/overlong");
    overlong.addHeader(RequestIdFilter.HEADER_NAME, "a".repeat(129));
    assertGeneratedUuid(overlong);

    MockHttpServletRequest control = new MockHttpServletRequest("GET", "/control");
    control.addHeader(RequestIdFilter.HEADER_NAME, "header\r\ninjection");
    assertGeneratedUuid(control);

    MockHttpServletRequest multiple = new MockHttpServletRequest("GET", "/multiple");
    multiple.addHeader(RequestIdFilter.HEADER_NAME, "first");
    multiple.addHeader(RequestIdFilter.HEADER_NAME, "second");
    assertGeneratedUuid(multiple);
  }

  private void assertGeneratedUuid(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

    String requestId = response.getHeader(RequestIdFilter.HEADER_NAME);
    assertThat(requestId).isNotBlank();
    assertThatCodeIsUuid(requestId);
    assertThat(request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE)).isEqualTo(requestId);
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  private static void assertThatCodeIsUuid(String requestId) {
    assertThat(UUID.fromString(requestId).toString()).isEqualTo(requestId);
  }
}
