package com.offertrack.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
@org.springframework.validation.annotation.Validated
public class JwtProperties {
  private String secret;
  private Duration accessTokenTtl = Duration.ofMinutes(15);

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public Duration getAccessTokenTtl() {
    return accessTokenTtl;
  }

  public void setAccessTokenTtl(Duration accessTokenTtl) {
    this.accessTokenTtl = accessTokenTtl;
  }

  @jakarta.validation.constraints.AssertTrue(
      message = "access token lifetime must be between 1 second and 15 minutes")
  public boolean isAccessLifetimeValid() {
    return accessTokenTtl != null
        && accessTokenTtl.compareTo(Duration.ofSeconds(1)) >= 0
        && accessTokenTtl.compareTo(Duration.ofMinutes(15)) <= 0;
  }
}
