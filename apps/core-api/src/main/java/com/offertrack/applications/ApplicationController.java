package com.offertrack.applications;

import com.offertrack.applications.dto.ApplicationBoardColumnResponse;
import com.offertrack.applications.dto.ApplicationBoardResponse;
import com.offertrack.applications.dto.ApplicationListResponse;
import com.offertrack.applications.dto.ApplicationResponse;
import com.offertrack.applications.dto.ApplicationWithInterviewsResponse;
import com.offertrack.applications.dto.CreateApplicationRequest;
import com.offertrack.applications.dto.ReplaceApplicationRequest;
import com.offertrack.applications.dto.UpdateApplicationStageRequest;
import com.offertrack.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationController {
  private final ApplicationService applicationService;

  public ApplicationController(ApplicationService applicationService) {
    this.applicationService = applicationService;
  }

  @PostMapping("/api/applications")
  @ResponseStatus(HttpStatus.CREATED)
  public ApplicationWithInterviewsResponse create(
      @AuthenticationPrincipal CurrentUser currentUser,
      @Valid @RequestBody CreateApplicationRequest request) {
    return applicationService.create(currentUser.id(), request);
  }

  @GetMapping("/api/applications")
  public ApplicationListResponse list(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String stage,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String direction) {
    return applicationService.list(
        currentUser.id(),
        ApplicationListQuery.fromRequestParams(page, size, search, stage, sort, direction));
  }

  @GetMapping("/api/applications/board")
  public ApplicationBoardResponse board(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) String search) {
    return applicationService.board(currentUser.id(), ApplicationBoardQuery.initial(search));
  }

  @GetMapping("/api/applications/board/columns/{stage}")
  public ApplicationBoardColumnResponse boardColumn(
      @AuthenticationPrincipal CurrentUser currentUser,
      @PathVariable String stage,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Integer offset) {
    return applicationService.boardColumn(
        currentUser.id(),
        ApplicationBoardQuery.parseStage(stage),
        ApplicationBoardQuery.column(search, offset));
  }

  @GetMapping("/api/applications/{id}")
  public ApplicationResponse get(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID id) {
    return applicationService.get(currentUser.id(), id);
  }

  @DeleteMapping("/api/applications/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal CurrentUser currentUser, @PathVariable UUID id) {
    applicationService.delete(currentUser.id(), id);
  }

  @PatchMapping("/api/applications/{id}/stage")
  public ApplicationResponse updateStage(
      @AuthenticationPrincipal CurrentUser currentUser,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateApplicationStageRequest request) {
    return applicationService.updateStage(currentUser.id(), id, request);
  }

  @PutMapping("/api/applications/{id}")
  public ApplicationWithInterviewsResponse replace(
      @AuthenticationPrincipal CurrentUser currentUser,
      @PathVariable UUID id,
      @Valid @RequestBody ReplaceApplicationRequest request) {
    return applicationService.replace(currentUser.id(), id, request);
  }
}
