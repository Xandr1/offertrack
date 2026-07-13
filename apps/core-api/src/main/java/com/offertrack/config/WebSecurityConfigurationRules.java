package com.offertrack.config;

import static com.offertrack.config.ConfigurationRuleSupport.invalid;
import static com.offertrack.config.ConfigurationRuleSupport.requireText;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class WebSecurityConfigurationRules {
  private static final Pattern COOKIE_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

  private WebSecurityConfigurationRules() {}

  static void validate(ProtectedConfigurationSnapshot configuration) {
    validateUrls(configuration);
    validateCookie(configuration);
  }

  private static void validateUrls(ProtectedConfigurationSnapshot configuration) {
    URI webUri = HostValidation.parseHttpUrl(configuration.webUrl(), "app.web.url", true);
    HostValidation.requireNonLoopbackHost(webUri.getHost(), "app.web.url");
    if (webUri.getRawQuery() != null
        || webUri.getRawFragment() != null
        || (StringUtils.hasText(webUri.getRawPath()) && !"/".equals(webUri.getRawPath()))) {
      invalid("app.web.url");
    }

    URI aiUri =
        HostValidation.parseHttpUrl(
            configuration.aiServiceBaseUrl(), "app.ai-service.base-url", false);
    HostValidation.requireNonLoopbackHost(aiUri.getHost(), "app.ai-service.base-url");
    if (aiUri.getRawQuery() != null || aiUri.getRawFragment() != null) {
      invalid("app.ai-service.base-url");
    }

    requireText(configuration.corsAllowedOrigins(), "app.cors.allowed-origins");
    Arrays.stream(configuration.corsAllowedOrigins().split(",", -1))
        .forEach(WebSecurityConfigurationRules::validateCorsOrigin);
  }

  private static void validateCorsOrigin(String origin) {
    URI uri = HostValidation.parseHttpUrl(origin, "app.cors.allowed-origins", true);
    if (!origin.equals("*")
        && uri.getRawUserInfo() == null
        && uri.getRawQuery() == null
        && uri.getRawFragment() == null
        && (uri.getRawPath() == null || uri.getRawPath().isEmpty())) {
      HostValidation.requireNonLoopbackHost(uri.getHost(), "app.cors.allowed-origins");
      return;
    }
    invalid("app.cors.allowed-origins");
  }

  private static void validateCookie(ProtectedConfigurationSnapshot configuration) {
    requireText(configuration.accessCookieName(), "app.auth.cookie.name");
    if (!COOKIE_NAME.matcher(configuration.accessCookieName()).matches()) {
      invalid("app.auth.cookie.name");
    }

    requireText(configuration.accessCookiePath(), "app.auth.cookie.path");
    if (!isValidCookiePath(configuration.accessCookiePath())) {
      invalid("app.auth.cookie.path");
    }

    if (!"true".equalsIgnoreCase(configuration.accessCookieSecure())) {
      invalid("app.auth.cookie.secure");
    }

    requireText(configuration.accessCookieSameSite(), "app.auth.cookie.same-site");
    if (Set.of("strict", "lax", "none").stream()
        .noneMatch(value -> value.equalsIgnoreCase(configuration.accessCookieSameSite()))) {
      invalid("app.auth.cookie.same-site");
    }

    if (StringUtils.hasText(configuration.accessCookieDomain())) {
      String domain = configuration.accessCookieDomain().trim();
      String withoutLeadingDot = domain.startsWith(".") ? domain.substring(1) : domain;
      if (HostValidation.isLoopbackHost(domain)
          || !HostValidation.isExplicitHostname(withoutLeadingDot)) {
        invalid("app.auth.cookie.domain");
      }
    }
  }

  private static boolean isValidCookiePath(String path) {
    if (!path.startsWith("/")) {
      return false;
    }
    for (int index = 0; index < path.length(); index++) {
      char character = path.charAt(index);
      if (character < 0x20 || character > 0x7e || character == ';') {
        return false;
      }
    }
    return true;
  }
}
