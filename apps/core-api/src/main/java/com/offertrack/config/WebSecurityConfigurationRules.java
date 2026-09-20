package com.offertrack.config;

import static com.offertrack.config.ConfigurationRuleSupport.invalid;
import static com.offertrack.config.ConfigurationRuleSupport.invalidException;
import static com.offertrack.config.ConfigurationRuleSupport.requireText;

import com.offertrack.applications.AiServiceAuthMode;
import com.offertrack.applications.AiServiceConfigurationException;
import com.offertrack.applications.AiServiceEndpointNormalizer;
import java.net.URI;
import java.util.Arrays;
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

    validateAiService(configuration);

    requireText(configuration.corsAllowedOrigins(), "app.cors.allowed-origins");
    Arrays.stream(configuration.corsAllowedOrigins().split(",", -1))
        .forEach(WebSecurityConfigurationRules::validateCorsOrigin);
    if (!configuration.webUrl().equals(configuration.corsAllowedOrigins())) {
      invalid("app.cors.allowed-origins");
    }
    URI core =
        HostValidation.parseHttpUrl(configuration.corePublicUrl(), "app.core.public-url", true);
    HostValidation.requireNonLoopbackHost(core.getHost(), "app.core.public-url");
    if (core.getRawQuery() != null
        || core.getRawFragment() != null
        || (core.getRawPath() != null && !core.getRawPath().isEmpty())
        || !core.getHost().equals("api." + webUri.getHost())) {
      invalid("app.core.public-url");
    }
    if (!(configuration.corePublicUrl() + "/login/oauth2/code/google")
        .equals(configuration.googleRedirectUri())) {
      invalid("spring.security.oauth2.client.registration.google.redirect-uri");
    }
  }

  private static void validateAiService(ProtectedConfigurationSnapshot configuration) {
    AiServiceAuthMode authMode;
    try {
      authMode = AiServiceAuthMode.fromConfiguration(configuration.aiServiceAuthMode());
    } catch (AiServiceConfigurationException exception) {
      throw invalidException(exception.property());
    }
    if (authMode != AiServiceAuthMode.GOOGLE_ID_TOKEN) {
      invalid("app.ai-service.auth-mode");
    }

    try {
      AiServiceEndpointNormalizer.NormalizedEndpoint baseEndpoint =
          AiServiceEndpointNormalizer.normalizeRoot(
              configuration.aiServiceBaseUrl(), "app.ai-service.base-url");
      AiServiceEndpointNormalizer.NormalizedEndpoint audienceEndpoint =
          AiServiceEndpointNormalizer.normalizeRoot(
              configuration.aiServiceAudience(), "app.ai-service.audience");
      HostValidation.requireProtectedServiceHost(baseEndpoint.host(), "app.ai-service.base-url");
      HostValidation.requireProtectedServiceHost(
          audienceEndpoint.host(), "app.ai-service.audience");
      if (!baseEndpoint.canonicalValue().equals(audienceEndpoint.canonicalValue())) {
        invalid("app.ai-service.audience");
      }
    } catch (AiServiceConfigurationException exception) {
      throw invalidException(exception.property());
    }
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
    if (!"/".equals(configuration.accessCookiePath())) {
      invalid("app.auth.cookie.path");
    }

    if (!"true".equalsIgnoreCase(configuration.accessCookieSecure())) {
      invalid("app.auth.cookie.secure");
    }

    requireText(configuration.accessCookieSameSite(), "app.auth.cookie.same-site");
    if (!"Lax".equals(configuration.accessCookieSameSite())) {
      invalid("app.auth.cookie.same-site");
    }

    if (StringUtils.hasText(configuration.accessCookieDomain())) {
      invalid("app.auth.cookie.domain");
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
