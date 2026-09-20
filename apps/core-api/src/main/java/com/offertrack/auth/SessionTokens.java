package com.offertrack.auth;

import java.time.Instant;

public record SessionTokens(
    String accessToken, String refreshToken, Instant accessExpiresAt, Instant refreshExpiresAt) {
  @Override
  public String toString() {
    return "SessionTokens[redacted]";
  }
}
