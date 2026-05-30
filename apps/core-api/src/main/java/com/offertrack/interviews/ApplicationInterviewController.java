package com.offertrack.interviews;

import com.offertrack.auth.CurrentUser;
import com.offertrack.interviews.dto.ApplicationInterviewResponse;
import com.offertrack.interviews.dto.UpdateInterviewStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationInterviewController {
  private final ApplicationInterviewService applicationInterviewService;

  public ApplicationInterviewController(ApplicationInterviewService applicationInterviewService) {
    this.applicationInterviewService = applicationInterviewService;
  }

  @GetMapping("/api/applications/{applicationId}/interviews")
  public List<ApplicationInterviewResponse> list(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID applicationId) {
    return applicationInterviewService.list(currentUser.id(), applicationId);
  }

  @PatchMapping("/api/applications/{applicationId}/interviews/{interviewId}/status")
  public ApplicationInterviewResponse updateStatus(
      @AuthenticationPrincipal CurrentUser currentUser,
      @PathVariable UUID applicationId,
      @PathVariable UUID interviewId,
      @Valid @RequestBody UpdateInterviewStatusRequest request) {
    return applicationInterviewService.updateStatus(
        currentUser.id(), applicationId, interviewId, request);
  }
}
