package com.offertrack.applications.dto;

import com.offertrack.applications.ApplicationStage;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ApplicationResponse(
    UUID id,
    String companyName,
    String positionTitle,
    String jobUrl,
    String location,
    String workMode,
    ApplicationStage stage,
    String notes,
    OffsetDateTime appliedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    NextInterviewResponse nextInterview) {}
