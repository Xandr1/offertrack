package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class AiServiceExtractionException extends DomainException {
  public AiServiceExtractionException() {
    super("AI_SERVICE_EXTRACTION_FAILED", "AI service could not extract a draft.");
  }
}
