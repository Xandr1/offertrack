package com.offertrack.applications.dto;

import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record CreateApplicationInterviewItemRequest(
    @NotNull(message = "Interview type is required") InterviewType type,
    InterviewStatus status,
    OffsetDateTime scheduledAt) {}
