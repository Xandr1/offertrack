package com.offertrack.ratelimit;

import java.util.List;

public class RateLimitExceededException extends RuntimeException {
  private final List<RateLimitPolicy> deniedPolicies;
  private final List<RateLimitSubjectType> subjectTypes;
  private final long retryAfterSeconds;

  public RateLimitExceededException(long retryAfterSeconds) {
    this(List.of(), List.of(), retryAfterSeconds);
  }

  public RateLimitExceededException(
      List<RateLimitPolicy> deniedPolicies,
      List<RateLimitSubjectType> subjectTypes,
      long retryAfterSeconds) {
    super("Too many requests. Try again later.");
    this.deniedPolicies = deniedPolicies.stream().distinct().toList();
    this.subjectTypes = subjectTypes.stream().distinct().toList();
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
  }

  public List<RateLimitPolicy> deniedPolicies() {
    return deniedPolicies;
  }

  public List<RateLimitSubjectType> subjectTypes() {
    return subjectTypes;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
