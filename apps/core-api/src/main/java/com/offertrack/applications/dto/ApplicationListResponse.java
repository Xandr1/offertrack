package com.offertrack.applications.dto;

import java.util.List;

public record ApplicationListResponse(
    List<ApplicationResponse> items, int page, int size, long totalItems, int totalPages) {}
