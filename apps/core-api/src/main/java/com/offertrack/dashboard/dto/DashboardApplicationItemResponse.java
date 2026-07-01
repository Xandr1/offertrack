package com.offertrack.dashboard.dto;

import com.offertrack.applications.ApplicationStage;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DashboardApplicationItemResponse(
    UUID applicationId,
    String companyName,
    String positionTitle,
    ApplicationStage stage,
    String jobUrl,
    String location,
    String workMode,
    OffsetDateTime appliedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
