package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class AiParserExtractionException extends DomainException {
  public AiParserExtractionException() {
    super("AI_PARSER_EXTRACTION_FAILED", "AI parser could not extract a draft.");
  }
}
