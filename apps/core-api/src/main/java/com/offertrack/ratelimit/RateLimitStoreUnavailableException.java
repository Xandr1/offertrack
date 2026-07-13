package com.offertrack.ratelimit;

final class RateLimitStoreUnavailableException extends RuntimeException {
  RateLimitStoreUnavailableException() {
    super("Rate-limit store is unavailable.");
  }
}
