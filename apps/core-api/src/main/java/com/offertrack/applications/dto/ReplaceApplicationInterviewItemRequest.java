package com.offertrack.applications.dto;

import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReplaceApplicationInterviewItemRequest(
    UUID id,
    @NotNull(message = "Interview type is required") InterviewType type,
    @NotNull(message = "Interview status is required") InterviewStatus status,
    OffsetDateTime scheduledAt) {}
