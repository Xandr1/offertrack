package com.offertrack.ratelimit;

public enum RateLimitPolicy {
  LOGIN_EMAIL("login-email"),
  LOGIN_IP("login-ip"),
  REGISTRATION_EMAIL("registration-email"),
  REGISTRATION_IP("registration-ip"),
  VERIFICATION_RESEND_EMAIL("verification-resend-email"),
  VERIFICATION_RESEND_IP("verification-resend-ip"),
  FORGOT_PASSWORD_EMAIL("forgot-password-email"),
  FORGOT_PASSWORD_IP("forgot-password-ip"),
  RESET_PASSWORD_TOKEN("reset-password-token"),
  RESET_PASSWORD_IP("reset-password-ip"),
  AI_USER_MINUTE("ai-user-minute"),
  AI_USER_DAY("ai-user-day");

  private final String key;

  RateLimitPolicy(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
