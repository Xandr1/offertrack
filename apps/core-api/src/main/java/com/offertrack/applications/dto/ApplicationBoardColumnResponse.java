package com.offertrack.applications.dto;

import com.offertrack.applications.ApplicationStage;
import java.util.List;

public record ApplicationBoardColumnResponse(
    ApplicationStage stage,
    long totalCount,
    List<ApplicationResponse> items,
    int nextOffset,
    boolean hasMore) {}
