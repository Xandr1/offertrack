package com.offertrack.ratelimit;

interface RateLimiter {
  RateLimitDecision consume(RateLimitAttempt attempt);
}
