package com.offertrack.applications.dto;

import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import java.time.OffsetDateTime;

public record ApplicationDraftInterviewResponse(
    InterviewType type, InterviewStatus status, OffsetDateTime scheduledAt) {}
