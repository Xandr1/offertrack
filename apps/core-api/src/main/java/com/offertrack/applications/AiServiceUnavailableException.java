package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class AiServiceUnavailableException extends DomainException {
  public AiServiceUnavailableException() {
    super("AI_SERVICE_UNAVAILABLE", "AI service is unavailable.");
  }
}
