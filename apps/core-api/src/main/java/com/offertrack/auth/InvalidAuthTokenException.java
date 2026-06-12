package com.offertrack.auth;

import com.offertrack.errors.DomainException;

public class InvalidAuthTokenException extends DomainException {
  public InvalidAuthTokenException() {
    super("INVALID_AUTH_TOKEN", "Verification link is invalid or expired.");
  }
}
