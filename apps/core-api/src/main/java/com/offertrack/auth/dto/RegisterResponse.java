package com.offertrack.auth.dto;

public record RegisterResponse(boolean emailVerificationRequired) {
  @Override
  public String toString() {
    return "RegisterResponse[redacted]";
  }
}
