package com.offertrack.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {
  private String authorizationRequestCookieSigningSecret;

  public String getAuthorizationRequestCookieSigningSecret() {
    return authorizationRequestCookieSigningSecret;
  }

  public void setAuthorizationRequestCookieSigningSecret(
      String authorizationRequestCookieSigningSecret) {
    this.authorizationRequestCookieSigningSecret = authorizationRequestCookieSigningSecret;
  }
}
