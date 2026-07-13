package com.offertrack.ratelimit;

record RateLimitDecision(boolean allowed, long retryAfterSeconds) {}
