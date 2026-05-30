package com.offertrack.interviews;

import com.offertrack.errors.DomainException;

public class InterviewNotFoundException extends DomainException {
  public InterviewNotFoundException() {
    super("INTERVIEW_NOT_FOUND", "Interview was not found");
  }
}
