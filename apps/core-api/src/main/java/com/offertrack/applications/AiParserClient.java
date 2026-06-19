package com.offertrack.applications;

import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.applications.dto.ApplicationDraftResponse;

public interface AiParserClient {
  ApplicationDraftResponse parseJob(ApplicationDraftRequest request);
}
