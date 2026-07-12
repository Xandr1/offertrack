package com.offertrack.ratelimit;

public enum RateLimitSubjectType {
  EMAIL("email"),
  IP("ip"),
  TOKEN("token"),
  USER("user");

  private final String key;

  RateLimitSubjectType(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
