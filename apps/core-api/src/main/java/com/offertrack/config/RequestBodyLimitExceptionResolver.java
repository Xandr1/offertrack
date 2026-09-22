package com.offertrack.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/** Return streamed overflows to the boundary filter before MVC can write a 400 response. */
public final class RequestBodyLimitExceptionResolver implements HandlerExceptionResolver, Ordered {
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public ModelAndView resolveException(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception exception) {
    RequestBodyLimitException.rethrowIfPresent(exception);
    return null;
  }
}
