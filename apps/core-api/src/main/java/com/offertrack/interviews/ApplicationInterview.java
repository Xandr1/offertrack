package com.offertrack.interviews;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApplicationInterview(
    UUID id,
    UUID userId,
    UUID applicationId,
    InterviewType type,
    InterviewStatus status,
    OffsetDateTime scheduledAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
