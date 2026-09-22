package com.offertrack.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.http")
public class HttpRequestProperties {
  @Min(1)
  private long maxRequestBodyBytes = 262144;

  public long getMaxRequestBodyBytes() {
    return maxRequestBodyBytes;
  }

  public void setMaxRequestBodyBytes(long maxRequestBodyBytes) {
    this.maxRequestBodyBytes = maxRequestBodyBytes;
  }
}
