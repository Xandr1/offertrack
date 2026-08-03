package com.offertrack.applications;

record ValidatedAiServiceConfiguration(
    String baseUrl, String internalApiKey, AiServiceAuthMode authMode, String audience) {

  static ValidatedAiServiceConfiguration from(AiServiceProperties properties) {
    String configuredBaseUrl = requireText(properties.getBaseUrl(), "app.ai-service.base-url");
    String baseUrl = configuredBaseUrl.trim();
    String internalApiKey =
        requireText(properties.getInternalApiKey(), "app.ai-service.internal-api-key").trim();
    AiServiceAuthMode authMode = AiServiceAuthMode.fromConfiguration(properties.getAuthMode());
    String audience = properties.getAudience();

    if (authMode == AiServiceAuthMode.GOOGLE_ID_TOKEN) {
      AiServiceEndpointNormalizer.NormalizedEndpoint baseEndpoint =
          AiServiceEndpointNormalizer.normalizeRoot(configuredBaseUrl, "app.ai-service.base-url");
      AiServiceEndpointNormalizer.NormalizedEndpoint audienceEndpoint =
          AiServiceEndpointNormalizer.normalizeRoot(audience, "app.ai-service.audience");
      if (!baseEndpoint.canonicalValue().equals(audienceEndpoint.canonicalValue())) {
        throw new AiServiceConfigurationException("app.ai-service.audience");
      }
    }

    return new ValidatedAiServiceConfiguration(
        baseUrl, internalApiKey, authMode, audience == null ? "" : audience);
  }

  private static String requireText(String value, String property) {
    if (value == null || value.isBlank()) {
      throw new AiServiceConfigurationException(property);
    }
    return value;
  }
}
