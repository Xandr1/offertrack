package com.offertrack.interviews;

import com.offertrack.applications.ApplicationNotFoundException;
import com.offertrack.applications.ApplicationRepository;
import com.offertrack.interviews.dto.ApplicationInterviewResponse;
import com.offertrack.interviews.dto.UpdateInterviewStatusRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApplicationInterviewService {
  private final ApplicationInterviewRepository applicationInterviewRepository;
  private final ApplicationRepository applicationRepository;

  public ApplicationInterviewService(
      ApplicationInterviewRepository applicationInterviewRepository,
      ApplicationRepository applicationRepository) {
    this.applicationInterviewRepository = applicationInterviewRepository;
    this.applicationRepository = applicationRepository;
  }

  public List<ApplicationInterviewResponse> list(UUID userId, UUID applicationId) {
    ensureApplicationExists(userId, applicationId);

    return applicationInterviewRepository.listByApplicationForUser(applicationId, userId).stream()
        .map(ApplicationInterviewResponseMapper::toResponse)
        .toList();
  }

  public ApplicationInterviewResponse updateStatus(
      UUID userId, UUID applicationId, UUID interviewId, UpdateInterviewStatusRequest request) {
    ApplicationInterview interview =
        applicationInterviewRepository
            .updateStatus(applicationId, interviewId, userId, request.status())
            .orElseThrow(InterviewNotFoundException::new);

    return ApplicationInterviewResponseMapper.toResponse(interview);
  }

  private void ensureApplicationExists(UUID userId, UUID applicationId) {
    applicationRepository
        .findByIdForUser(applicationId, userId)
        .orElseThrow(ApplicationNotFoundException::new);
  }
}
