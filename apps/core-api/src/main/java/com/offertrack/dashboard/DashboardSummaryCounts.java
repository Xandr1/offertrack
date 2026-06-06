package com.offertrack.dashboard;

public record DashboardSummaryCounts(
    long activeProcesses, long interviewing, long offers, long rejected) {}
