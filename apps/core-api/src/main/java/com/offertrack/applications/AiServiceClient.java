package com.offertrack.applications;

import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.applications.dto.ApplicationDraftResponse;

public interface AiServiceClient {
  ApplicationDraftResponse parseJob(ApplicationDraftRequest request);
}
