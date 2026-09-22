package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.servlet.ServletException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestBodyLimitFilterTest {
  private RequestBodyLimitFilter filter() {
    HttpRequestProperties properties = new HttpRequestProperties();
    properties.setMaxRequestBodyBytes(8);
    return new RequestBodyLimitFilter(properties, JsonMapper.builder().findAndAddModules().build());
  }

  private MockHttpServletRequest request(String body, boolean unknownLength) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/auth/login") {
          @Override
          public long getContentLengthLong() {
            return unknownLength ? -1 : super.getContentLengthLong();
          }

          @Override
          public int getContentLength() {
            return unknownLength ? -1 : super.getContentLength();
          }
        };
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    request.setCharacterEncoding("UTF-8");
    return request;
  }

  @ParameterizedTest
  @ValueSource(strings = {"1234567", "12345678", "éééé"})
  void acceptsBodiesUpToExactByteBoundary(String body) throws Exception {
    for (boolean reader : new boolean[] {false, true}) {
      var response = new MockHttpServletResponse();
      filter()
          .doFilter(
              request(body, true),
              response,
              (req, res) -> {
                if (reader) assertThat(req.getReader().readLine()).isEqualTo(body);
                else
                  assertThat(req.getInputStream().readAllBytes())
                      .isEqualTo(body.getBytes(StandardCharsets.UTF_8));
              });
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

  @Test
  void rejectsDeclaredSizeBeforeCallingDownstream() throws Exception {
    AtomicBoolean called = new AtomicBoolean();
    var response = new MockHttpServletResponse();
    filter().doFilter(request("123456789", false), response, (req, res) -> called.set(true));
    assertThat(called).isFalse();
    assertThat(response.getStatus()).isEqualTo(413);
  }

  @Test
  void rejectsUnknownLengthStreamOrReaderWithoutResettingSecurityHeaders() throws Exception {
    for (boolean reader : new boolean[] {false, true}) {
      var response = new MockHttpServletResponse();
      response.setHeader("Access-Control-Allow-Origin", "https://app.example.test");
      response.setHeader("X-Request-Id", "test-request");
      filter()
          .doFilter(
              request("123456789", true),
              response,
              (req, res) -> {
                if (reader) req.getReader().readLine();
                else req.getInputStream().readAllBytes();
              });
      assertThat(response.getStatus()).isEqualTo(413);
      assertThat(response.getContentAsString())
          .contains("PAYLOAD_TOO_LARGE")
          .doesNotContain("123456789");
      assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
      assertThat(response.getHeader("X-Request-Id")).isEqualTo("test-request");
      assertThat(response.getHeader("Access-Control-Allow-Origin"))
          .isEqualTo("https://app.example.test");
    }
  }

  @Test
  void ownsDirectAndWrappedExceptionsAndClearsOnlyUncommittedBody() throws Exception {
    for (boolean wrapped : new boolean[] {false, true}) {
      var response = new MockHttpServletResponse();
      filter()
          .doFilter(
              request("", true),
              response,
              (req, res) -> {
                res.getOutputStream().write("partial".getBytes(StandardCharsets.UTF_8));
                if (wrapped)
                  throw new ServletException(
                      new IllegalArgumentException(new RequestBodyLimitException()));
                throw new RequestBodyLimitException();
              });
      assertThat(response.getStatus()).isEqualTo(413);
      assertThat(response.getContentAsString())
          .contains("PAYLOAD_TOO_LARGE")
          .doesNotContain("partial");
    }
  }

  @Test
  void handlesOverflowAfterAnUncommittedWriterWasObtained() throws Exception {
    var response =
        new MockHttpServletResponse() {
          @Override
          public jakarta.servlet.ServletOutputStream getOutputStream() {
            throw new IllegalStateException("Writer already obtained");
          }
        };
    filter()
        .doFilter(
            request("123456789", true),
            response,
            (req, res) -> {
              res.getWriter().write("partial");
              req.getInputStream().readAllBytes();
            });
    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString())
        .contains("PAYLOAD_TOO_LARGE")
        .doesNotContain("partial");
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
  void bypassesBodylessMethodsIncludingOAuthCallback(String method) throws Exception {
    var request = request("123456789", false);
    request.setMethod(method);
    request.setRequestURI("/login/oauth2/code/google");
    AtomicBoolean called = new AtomicBoolean();
    filter()
        .doFilter(
            request,
            new MockHttpServletResponse(),
            (req, res) -> {
              called.set(true);
              assertThat(req).isSameAs(request);
            });
    assertThat(called).isTrue();
  }
}
