package com.offertrack.applications;

import com.offertrack.applications.dto.ApplicationResponse;
import com.offertrack.applications.dto.NextInterviewResponse;

public class ApplicationResponseMapper {
  private ApplicationResponseMapper() {}

  public static ApplicationResponse toResponse(
      Application application,
      NextInterviewResponse nextInterview,
      NextInterviewResponse lastInterview) {
    return new ApplicationResponse(
        application.id(),
        application.companyName(),
        application.positionTitle(),
        application.jobUrl(),
        application.location(),
        application.workMode(),
        application.stage(),
        application.notes(),
        application.appliedAt(),
        application.followedUpAt(),
        application.createdAt(),
        application.updatedAt(),
        nextInterview,
        lastInterview);
  }

  public static ApplicationResponse toResponse(Application application) {
    return toResponse(application, null, null);
  }
}
