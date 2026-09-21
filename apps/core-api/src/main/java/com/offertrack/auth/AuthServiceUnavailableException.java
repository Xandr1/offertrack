package com.offertrack.auth;

/** Never retain a database exception: it can contain credentials or identifying values. */
public final class AuthServiceUnavailableException extends RuntimeException {
  public AuthServiceUnavailableException() {
    super("Authentication service unavailable");
  }
}
