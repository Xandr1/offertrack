package com.offertrack.applications.dto;

import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NextInterviewResponse(
    UUID id, InterviewType type, InterviewStatus status, OffsetDateTime scheduledAt) {}
