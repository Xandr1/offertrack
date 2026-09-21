package com.offertrack.auth.dto;

public record VerifyEmailResponse(boolean verified) {
  @Override
  public String toString() {
    return "VerifyEmailResponse[redacted]";
  }
}
