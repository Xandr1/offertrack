package com.offertrack.interviews.dto;

import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ApplicationInterviewResponse(
    UUID id,
    UUID applicationId,
    InterviewType type,
    InterviewStatus status,
    OffsetDateTime scheduledAt,
    OffsetDateTime followedUpAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
  public ApplicationInterviewResponse(
      UUID id,
      UUID applicationId,
      InterviewType type,
      InterviewStatus status,
      OffsetDateTime scheduledAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this(id, applicationId, type, status, scheduledAt, null, createdAt, updatedAt);
  }
}
