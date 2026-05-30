package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class InvalidInterviewCountException extends DomainException {
  public InvalidInterviewCountException(int maxInterviews) {
    super("INVALID_INTERVIEW_COUNT", "Maximum " + maxInterviews + " interviews per application");
  }
}
