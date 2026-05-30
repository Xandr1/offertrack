package com.offertrack.interviews.dto;

import com.offertrack.interviews.InterviewStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateInterviewStatusRequest(
    @NotNull(message = "Interview status is required") InterviewStatus status) {}
