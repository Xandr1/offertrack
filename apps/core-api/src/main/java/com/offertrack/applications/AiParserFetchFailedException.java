package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class AiParserFetchFailedException extends DomainException {
  public AiParserFetchFailedException() {
    super("AI_PARSER_FETCH_FAILED", "AI parser could not fetch the job URL.");
  }
}
