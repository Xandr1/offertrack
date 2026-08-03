package com.offertrack.config;

public record DatabaseConfiguration(String url, String username, String password) {
  @Override
  public String toString() {
    return "DatabaseConfiguration[values=redacted]";
  }
}
