package com.offertrack.applications;

import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import org.springframework.stereotype.Service;

@Service
public class ApplicationDraftService {
  private final AiServiceClient aiServiceClient;

  public ApplicationDraftService(AiServiceClient aiServiceClient) {
    this.aiServiceClient = aiServiceClient;
  }

  public ApplicationDraftResponse createDraft(ApplicationDraftRequest request) {
    return aiServiceClient.parseJob(request);
  }
}
