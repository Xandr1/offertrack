package com.offertrack.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertrack.errors.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public final class RequestBodyLimitFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestBodyLimitFilter.class);
  private static final Set<String> BODYLESS_METHODS = Set.of("GET", "HEAD", "OPTIONS");
  private final long limit;
  private final ObjectMapper mapper;

  public RequestBodyLimitFilter(HttpRequestProperties properties, ObjectMapper mapper) {
    this.limit = properties.getMaxRequestBodyBytes();
    this.mapper = mapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return BODYLESS_METHODS.contains(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      if (request.getContentLengthLong() > limit) {
        throw new RequestBodyLimitException();
      }
      filterChain.doFilter(new LimitedRequest(request, limit), response);
    } catch (IOException | ServletException | RuntimeException exception) {
      RequestBodyLimitException overflow = RequestBodyLimitException.find(exception);
      if (overflow == null || response.isCommitted()) {
        throw exception;
      }
      response.resetBuffer();
      response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
      response.setHeader("Cache-Control", "no-store");
      response.setContentType("application/json");
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      ApiErrorResponse error =
          ApiErrorResponse.of(
              413, "PAYLOAD_TOO_LARGE", overflow.getMessage(), request.getRequestURI());
      // resetBuffer preserves the chosen servlet output mode as well as headers.
      // A downstream component may already have obtained a writer before the overflow.
      java.io.OutputStream output;
      try {
        output = response.getOutputStream();
      } catch (IllegalStateException writerAlreadyObtained) {
        mapper.writeValue(response.getWriter(), error);
        logRejection(request);
        return;
      }
      mapper.writeValue(output, error);
      logRejection(request);
    }
  }

  private static void logRejection(HttpServletRequest request) {
    log.warn(
        "http_request_rejected method={} status=413 error_code=PAYLOAD_TOO_LARGE request_id={}",
        request.getMethod(),
        RequestIdFilter.requestId(request));
  }

  private static final class LimitedRequest extends HttpServletRequestWrapper {
    private final long limit;
    private ServletInputStream stream;
    private BufferedReader reader;

    private LimitedRequest(HttpServletRequest request, long limit) {
      super(request);
      this.limit = limit;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (reader != null) {
        throw new IllegalStateException("Reader already obtained");
      }
      return limitedStream();
    }

    private ServletInputStream limitedStream() throws IOException {
      if (stream == null) {
        stream = new LimitedStream(super.getInputStream(), limit);
      }
      return stream;
    }

    @Override
    public BufferedReader getReader() throws IOException {
      if (reader == null) {
        if (stream != null) {
          throw new IllegalStateException("Input stream already obtained");
        }
        String encoding = getCharacterEncoding();
        reader =
            new BufferedReader(
                new InputStreamReader(
                    limitedStream(),
                    encoding == null ? StandardCharsets.ISO_8859_1.name() : encoding));
      }
      return reader;
    }
  }

  private static final class LimitedStream extends ServletInputStream {
    private final ServletInputStream delegate;
    private long remaining;
    private boolean exceeded;

    private LimitedStream(ServletInputStream delegate, long limit) {
      this.delegate = delegate;
      this.remaining = limit;
    }

    private void consumed(int count) {
      if (exceeded || count > remaining) {
        exceeded = true;
        throw new RequestBodyLimitException();
      }
      if (count > 0) remaining -= count;
    }

    @Override
    public int read() throws IOException {
      if (exceeded) throw new RequestBodyLimitException();
      int value = delegate.read();
      consumed(value == -1 ? 0 : 1);
      return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      if (exceeded) throw new RequestBodyLimitException();
      // Read at most one byte beyond the limit; never buffer the entire body.
      int boundedLength =
          (int) Math.min(length, remaining >= Integer.MAX_VALUE ? length : remaining + 1);
      int count = delegate.read(bytes, offset, boundedLength);
      consumed(count);
      return count;
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      delegate.setReadListener(listener);
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
