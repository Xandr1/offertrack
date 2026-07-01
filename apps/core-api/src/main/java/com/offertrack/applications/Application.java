package com.offertrack.applications;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Application(
    UUID id,
    UUID userId,
    String companyName,
    String positionTitle,
    String jobUrl,
    String location,
    String workMode,
    ApplicationStage stage,
    String notes,
    OffsetDateTime appliedAt,
    OffsetDateTime followedUpAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
  public Application(
      UUID id,
      UUID userId,
      String companyName,
      String positionTitle,
      String jobUrl,
      String location,
      String workMode,
      ApplicationStage stage,
      String notes,
      OffsetDateTime appliedAt,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this(
        id,
        userId,
        companyName,
        positionTitle,
        jobUrl,
        location,
        workMode,
        stage,
        notes,
        appliedAt,
        null,
        createdAt,
        updatedAt);
  }
}
