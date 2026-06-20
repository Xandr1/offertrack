package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class AiServiceInvalidUrlException extends DomainException {
  public AiServiceInvalidUrlException() {
    super("AI_SERVICE_INVALID_URL", "Job URL is invalid or unsafe.");
  }
}
