package com.offertrack.dashboard.dto;

import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DashboardInterviewItemResponse(
    UUID applicationId,
    UUID interviewId,
    String companyName,
    String positionTitle,
    String jobUrl,
    String location,
    String workMode,
    OffsetDateTime scheduledAt,
    InterviewType interviewType,
    InterviewStatus status) {}
