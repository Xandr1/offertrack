package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class AiServiceFetchFailedException extends DomainException {
  public AiServiceFetchFailedException() {
    super("AI_SERVICE_FETCH_FAILED", "AI service could not fetch the job URL.");
  }
}
