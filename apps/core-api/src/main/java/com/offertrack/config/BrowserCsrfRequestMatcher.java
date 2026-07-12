package com.offertrack.config;

import com.offertrack.auth.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.UrlPathHelper;

public final class BrowserCsrfRequestMatcher implements RequestMatcher {
  private static final UrlPathHelper URL_PATH_HELPER = UrlPathHelper.defaultInstance;
  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "TRACE", "OPTIONS");
  private static final Set<String> PUBLIC_AUTH_MUTATIONS =
      Set.of(
          "/auth/register",
          "/auth/login",
          "/auth/logout",
          "/auth/email/verify",
          "/auth/email/verification/resend",
          "/auth/password/forgot",
          "/auth/password/reset");

  private final String accessTokenCookieName;

  public BrowserCsrfRequestMatcher(AuthCookieProperties cookieProperties) {
    this.accessTokenCookieName = cookieProperties.getName();
  }

  @Override
  public boolean matches(HttpServletRequest request) {
    if (SAFE_METHODS.contains(request.getMethod())) {
      return false;
    }

    String requestPath = URL_PATH_HELPER.getPathWithinApplication(request);
    if (PUBLIC_AUTH_MUTATIONS.contains(requestPath)) {
      return true;
    }

    Cookie[] cookies = request.getCookies();
    return cookies != null
        && Arrays.stream(cookies)
            .anyMatch(cookie -> accessTokenCookieName.equals(cookie.getName()));
  }
}
