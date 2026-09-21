package com.offertrack.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.auth.session")
public class SessionProperties {
  private Duration inactivityTtl = Duration.ofDays(7);
  private Duration absoluteTtl = Duration.ofDays(30);

  @Min(1)
  @Max(10)
  private int maxActive = 10;

  public Duration getInactivityTtl() {
    return inactivityTtl;
  }

  public void setInactivityTtl(Duration value) {
    inactivityTtl = value;
  }

  public Duration getAbsoluteTtl() {
    return absoluteTtl;
  }

  public void setAbsoluteTtl(Duration value) {
    absoluteTtl = value;
  }

  public int getMaxActive() {
    return maxActive;
  }

  public void setMaxActive(int value) {
    maxActive = value;
  }

  @AssertTrue(message = "session lifetimes must be positive and bounded by 7 and 30 days")
  public boolean isLifetimeValid() {
    return inactivityTtl != null
        && absoluteTtl != null
        && inactivityTtl.compareTo(Duration.ofSeconds(1)) >= 0
        && inactivityTtl.compareTo(Duration.ofDays(7)) <= 0
        && absoluteTtl.compareTo(inactivityTtl) >= 0
        && absoluteTtl.compareTo(Duration.ofDays(30)) <= 0;
  }
}
