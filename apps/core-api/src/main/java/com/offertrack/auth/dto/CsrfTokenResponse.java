package com.offertrack.auth.dto;

public record CsrfTokenResponse(String token, String headerName) {
  @Override
  public String toString() {
    return "CsrfTokenResponse[redacted]";
  }
}
