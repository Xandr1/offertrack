package com.offertrack.applications;

import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import com.offertrack.auth.CurrentUser;
import com.offertrack.ratelimit.RateLimitGuard;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationDraftController {
  private final ApplicationDraftService applicationDraftService;
  private final RateLimitGuard rateLimitGuard;

  public ApplicationDraftController(
      ApplicationDraftService applicationDraftService, RateLimitGuard rateLimitGuard) {
    this.applicationDraftService = applicationDraftService;
    this.rateLimitGuard = rateLimitGuard;
  }

  @PostMapping("/api/applications/draft")
  public ApplicationDraftResponse createDraft(
      @Valid @RequestBody ApplicationDraftRequest request,
      @AuthenticationPrincipal CurrentUser currentUser) {
    rateLimitGuard.checkAiDraft(currentUser.id());
    return applicationDraftService.createDraft(request);
  }
}
