package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class AiParserInvalidUrlException extends DomainException {
  public AiParserInvalidUrlException() {
    super("AI_PARSER_INVALID_URL", "Job URL is invalid or unsafe.");
  }
}
