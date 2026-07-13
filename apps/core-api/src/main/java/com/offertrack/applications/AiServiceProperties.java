package com.offertrack.applications;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-service")
public class AiServiceProperties {
  private String baseUrl = "http://localhost:8000";
  private String internalApiKey = "local-dev-ai-service-key";

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
}
