package com.offertrack.applications.dto;

import com.offertrack.applications.ApplicationStage;
import java.util.List;

public record ApplicationDraftResponse(
    String companyName,
    String positionTitle,
    String jobUrl,
    String location,
    String workMode,
    ApplicationStage stage,
    String notes,
    List<ApplicationDraftInterviewResponse> interviews,
    List<String> warnings) {}
