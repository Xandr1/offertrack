package com.offertrack.auth.dto;

import java.util.UUID;

public record AuthResponse(UserSummary user) {
  public record UserSummary(UUID id, String email, String name) {
    @Override
    public String toString() {
      return "UserSummary[redacted]";
    }
  }
}
