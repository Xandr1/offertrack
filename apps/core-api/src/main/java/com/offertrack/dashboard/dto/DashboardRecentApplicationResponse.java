package com.offertrack.dashboard.dto;

import com.offertrack.applications.ApplicationStage;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DashboardRecentApplicationResponse(
    UUID id,
    String companyName,
    String positionTitle,
    ApplicationStage stage,
    OffsetDateTime updatedAt) {}
