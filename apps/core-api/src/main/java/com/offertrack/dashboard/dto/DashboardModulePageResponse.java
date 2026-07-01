package com.offertrack.dashboard.dto;

import java.util.List;

public record DashboardModulePageResponse<T>(
    long totalCount, List<T> items, int nextOffset, boolean hasMore) {}
