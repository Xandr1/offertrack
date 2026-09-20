package com.offertrack.auth;

import java.util.UUID;

public record CurrentUser(UUID id, UUID sessionId) {
  @Override
  public String toString() {
    return "CurrentUser[redacted]";
  }
}
