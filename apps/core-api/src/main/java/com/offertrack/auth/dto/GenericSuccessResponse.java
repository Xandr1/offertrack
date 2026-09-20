package com.offertrack.auth.dto;

public record GenericSuccessResponse(boolean ok) {
  @Override
  public String toString() {
    return "GenericSuccessResponse[redacted]";
  }
}
