package com.offertrack.applications.dto;

import com.offertrack.applications.ApplicationStage;
import jakarta.validation.constraints.NotNull;

public record UpdateApplicationStageRequest(
    @NotNull(message = "Application stage is required") ApplicationStage stage) {}
