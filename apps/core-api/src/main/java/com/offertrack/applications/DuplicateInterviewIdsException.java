package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class DuplicateInterviewIdsException extends DomainException {
  public DuplicateInterviewIdsException() {
    super("DUPLICATE_INTERVIEW_IDS", "Duplicate interview ids");
  }
}
