package com.offertrack.ratelimit;

record RateLimitAttempt(
    RateLimitPolicy policy,
    RateLimitSubjectType subjectType,
    String subject,
    RateLimitProperties.Policy configuration) {}
