package com.offertrack.applications;

import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import org.springframework.stereotype.Service;

@Service
public class ApplicationDraftService {
  private final AiParserClient aiParserClient;

  public ApplicationDraftService(AiParserClient aiParserClient) {
    this.aiParserClient = aiParserClient;
  }

  public ApplicationDraftResponse createDraft(ApplicationDraftRequest request) {
    return aiParserClient.parseJob(request);
  }
}
