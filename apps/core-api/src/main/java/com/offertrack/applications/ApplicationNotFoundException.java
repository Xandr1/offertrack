package com.offertrack.applications;

import com.offertrack.errors.DomainException;

public class ApplicationNotFoundException extends DomainException {
  public ApplicationNotFoundException() {
    super("APPLICATION_NOT_FOUND", "Application was not found");
  }
}
