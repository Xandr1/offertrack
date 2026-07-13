package com.offertrack.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth.cookie")
public class AuthCookieProperties {
  private static final String DEFAULT_NAME = "access_token";

  @NotBlank private String name = DEFAULT_NAME;

  @NotBlank private String path = "/";

  private String domain;
  private boolean secure;

  @NotBlank
  @Pattern(regexp = "(?i)Strict|Lax|None")
  private String sameSite = "Lax";

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public boolean isSecure() {
    return secure;
  }

  public void setSecure(boolean secure) {
    this.secure = secure;
  }

  public String getSameSite() {
    return sameSite;
  }

  public void setSameSite(String sameSite) {
    this.sameSite = sameSite;
  }

  @AssertTrue(message = "cookie path must begin with '/'")
  public boolean isPathValid() {
    return path != null && path.startsWith("/");
  }

  @AssertTrue(message = "SameSite=None cookies must be Secure")
  public boolean isSameSiteSecure() {
    return sameSite == null || !"none".equalsIgnoreCase(sameSite) || secure;
  }
}
