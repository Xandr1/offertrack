package com.offertrack.ratelimit;

public class RateLimitServiceUnavailableException extends RuntimeException {
  public RateLimitServiceUnavailableException() {
    super("Security service is temporarily unavailable.");
  }
}
