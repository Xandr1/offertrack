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
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
