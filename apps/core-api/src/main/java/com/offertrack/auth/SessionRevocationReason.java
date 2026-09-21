package com.offertrack.auth;

public enum SessionRevocationReason {
  LOGOUT("logout"),
  LOGOUT_ALL("logout_all"),
  PASSWORD_RESET("password_reset"),
  OAUTH_ACCOUNT_CLAIM("oauth_account_claim"),
  REFRESH_REPLAY("refresh_replay"),
  SESSION_LIMIT("session_limit");

  private final String value;

  SessionRevocationReason(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
