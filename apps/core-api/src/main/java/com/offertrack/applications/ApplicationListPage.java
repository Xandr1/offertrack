package com.offertrack.applications;

import java.util.List;

public record ApplicationListPage(
    List<Application> items, int page, int size, long totalItems, int totalPages) {}
