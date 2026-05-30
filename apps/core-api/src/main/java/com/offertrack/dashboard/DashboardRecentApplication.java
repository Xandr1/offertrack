package com.offertrack.dashboard;

import com.offertrack.applications.ApplicationStage;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DashboardRecentApplication(
    UUID id,
    String companyName,
    String positionTitle,
    ApplicationStage stage,
    OffsetDateTime updatedAt) {}
