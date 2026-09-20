package com.offertrack.auth;

public final class AuthenticationRequiredException extends RuntimeException {
  public AuthenticationRequiredException() {
    super("Authentication required");
  }
}
