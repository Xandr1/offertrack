package com.offertrack.applications;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-service")
public class AiServiceProperties {
  private String baseUrl = "http://localhost:8000";
  private String internalApiKey = "local-dev-ai-service-key";
  private String authMode = "internal-key";
  private String audience = "";

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getInternalApiKey() {
    return internalApiKey;
  }

  public void setInternalApiKey(String internalApiKey) {
    this.internalApiKey = internalApiKey;
  }

  public String getAuthMode() {
    return authMode;
  }

  public void setAuthMode(String authMode) {
    this.authMode = authMode;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }
}
