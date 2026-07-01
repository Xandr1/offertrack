package com.offertrack.applications.cache;

public record AiDraftCacheKey(
    String redisKey, String normalizedUrl, String host, String hashSuffix) {}
