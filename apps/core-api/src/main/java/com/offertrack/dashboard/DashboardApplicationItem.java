package com.offertrack.dashboard;

import com.offertrack.applications.ApplicationStage;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DashboardApplicationItem(
    UUID applicationId,
    String companyName,
    String positionTitle,
    ApplicationStage stage,
    String jobUrl,
    String location,
    String workMode,
    OffsetDateTime appliedAt,
    OffsetDateTime updatedAt) {}
