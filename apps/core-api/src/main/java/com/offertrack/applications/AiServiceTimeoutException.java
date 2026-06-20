package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class AiServiceTimeoutException extends DomainException {
  public AiServiceTimeoutException() {
    super("AI_SERVICE_TIMEOUT", "AI service timed out.");
  }
}
