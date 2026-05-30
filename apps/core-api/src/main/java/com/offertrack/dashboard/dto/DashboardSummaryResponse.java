package com.offertrack.dashboard.dto;

import java.util.List;

public record DashboardSummaryResponse(
    long activeProcesses,
    long needsAttention,
    long interviewing,
    long offers,
    long rejected,
    List<DashboardRecentApplicationResponse> recentApplications) {}
