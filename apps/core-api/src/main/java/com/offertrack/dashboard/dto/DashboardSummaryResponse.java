package com.offertrack.dashboard.dto;

public record DashboardSummaryResponse(
    long activeProcesses,
    long needsAttention,
    long interviewing,
    long offers,
    long rejected,
    int followUpAfterApplyingDays,
    int upcomingInterviewDays,
    int followUpAfterInterviewDays,
    DashboardModulePageResponse<DashboardApplicationItemResponse> applicationsToFollowUp,
    DashboardModulePageResponse<DashboardInterviewItemResponse> upcomingInterviews,
    DashboardModulePageResponse<DashboardInterviewItemResponse> interviewsToFollowUp) {}
