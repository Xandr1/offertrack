package com.offertrack.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestIdFilter extends OncePerRequestFilter {
  public static final String HEADER_NAME = "X-Request-Id";
  public static final String REQUEST_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
  public static final String MDC_KEY = "request_id";

  private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = acceptedClientRequestId(request);
    request.setAttribute(REQUEST_ATTRIBUTE, requestId);
    response.setHeader(HEADER_NAME, requestId);
    MDC.put(MDC_KEY, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  public static String currentRequestId() {
    String requestId = MDC.get(MDC_KEY);
    return isValid(requestId) ? requestId : UUID.randomUUID().toString();
  }

  public static String requestId(HttpServletRequest request) {
    Object requestId = request.getAttribute(REQUEST_ATTRIBUTE);
    if (requestId instanceof String value && isValid(value)) {
      return value;
    }

    String generated = UUID.randomUUID().toString();
    request.setAttribute(REQUEST_ATTRIBUTE, generated);
    return generated;
  }

  private static String acceptedClientRequestId(HttpServletRequest request) {
    var values = Collections.list(request.getHeaders(HEADER_NAME));
    if (values.size() == 1 && isValid(values.getFirst())) {
      return values.getFirst();
    }
    return UUID.randomUUID().toString();
  }

  private static boolean isValid(String value) {
    return value != null && VALID_REQUEST_ID.matcher(value).matches();
  }
}
