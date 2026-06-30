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
    OffsetDateTime followedUpAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
  public ApplicationInterview(
      UUID id,
      UUID userId,
      UUID applicationId,
      InterviewType type,
      InterviewStatus status,
      OffsetDateTime scheduledAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this(id, userId, applicationId, type, status, scheduledAt, null, createdAt, updatedAt);
  }
}
