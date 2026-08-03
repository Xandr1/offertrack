package com.offertrack.applications;

final class AiServiceIdentityTokenException extends RuntimeException {
  AiServiceIdentityTokenException() {
    super("AI service identity token is unavailable");
  }
}
