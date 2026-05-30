package com.offertrack.dashboard;

public record DashboardSummaryCounts(
    long activeProcesses, long needsAttention, long interviewing, long offers, long rejected) {}
