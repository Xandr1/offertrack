package com.offertrack.dashboard.dto;

import java.util.List;

public record DashboardSummaryResponse(
    long activeProcesses,
    long needsAttention,
    long interviewing,
    long offers,
    long rejected,
    long draftsToApplyCount,
    long applicationsToFollowUpCount,
    long upcomingInterviewsCount,
    long interviewsToFollowUpCount,
    int followUpAfterApplyingDays,
    int upcomingInterviewDays,
    int followUpAfterInterviewDays,
    List<DashboardApplicationItemResponse> draftsToApply,
    List<DashboardApplicationItemResponse> applicationsToFollowUp,
    List<DashboardInterviewItemResponse> upcomingInterviews,
    List<DashboardInterviewItemResponse> interviewsToFollowUp) {}
