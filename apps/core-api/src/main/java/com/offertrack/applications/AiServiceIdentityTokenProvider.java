package com.offertrack.applications;

@FunctionalInterface
public interface AiServiceIdentityTokenProvider {
  String getToken(String audience);
}
