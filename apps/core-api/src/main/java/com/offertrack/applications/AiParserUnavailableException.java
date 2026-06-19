package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class AiParserUnavailableException extends DomainException {
  public AiParserUnavailableException() {
    super("AI_PARSER_UNAVAILABLE", "AI parser service is unavailable.");
  }
}
