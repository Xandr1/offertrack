package com.offertrack.auth;

import com.offertrack.errors.DomainException;

public class EmailNotVerifiedException extends DomainException {
  public EmailNotVerifiedException() {
    super("EMAIL_NOT_VERIFIED", "Please verify your email before signing in.");
  }
}
