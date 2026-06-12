package com.offertrack.auth;

public enum AuthTokenPurpose {
  EMAIL_VERIFICATION("email_verification"),
  PASSWORD_RESET("password_reset");

  private final String value;

  AuthTokenPurpose(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
