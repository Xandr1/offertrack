package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class AiParserTimeoutException extends DomainException {
  public AiParserTimeoutException() {
    super("AI_PARSER_TIMEOUT", "AI parser service timed out.");
  }
}
