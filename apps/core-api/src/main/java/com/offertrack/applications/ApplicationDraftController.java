package com.offertrack.applications;

import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationDraftController {
  private final ApplicationDraftService applicationDraftService;

  public ApplicationDraftController(ApplicationDraftService applicationDraftService) {
    this.applicationDraftService = applicationDraftService;
  }

  @PostMapping("/api/applications/draft")
  public ApplicationDraftResponse createDraft(@Valid @RequestBody ApplicationDraftRequest request) {
    return applicationDraftService.createDraft(request);
  }
}
