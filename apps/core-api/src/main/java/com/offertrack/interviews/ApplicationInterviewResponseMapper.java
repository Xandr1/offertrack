package com.offertrack.interviews;

import com.offertrack.interviews.dto.ApplicationInterviewResponse;

public class ApplicationInterviewResponseMapper {
  private ApplicationInterviewResponseMapper() {}

  public static ApplicationInterviewResponse toResponse(ApplicationInterview interview) {
    return new ApplicationInterviewResponse(
        interview.id(),
        interview.applicationId(),
        interview.type(),
        interview.status(),
        interview.scheduledAt(),
        interview.followedUpAt(),
        interview.createdAt(),
        interview.updatedAt());
  }
}
