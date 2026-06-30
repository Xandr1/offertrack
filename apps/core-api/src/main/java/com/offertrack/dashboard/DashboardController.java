package com.offertrack.dashboard;

import com.offertrack.auth.CurrentUser;
import com.offertrack.dashboard.dto.DashboardApplicationItemResponse;
import com.offertrack.dashboard.dto.DashboardInterviewItemResponse;
import com.offertrack.dashboard.dto.DashboardModulePageResponse;
import com.offertrack.dashboard.dto.DashboardSummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @GetMapping("/api/dashboard/summary")
  public DashboardSummaryResponse getSummary(@AuthenticationPrincipal CurrentUser currentUser) {
    return dashboardService.getSummary(currentUser.id());
  }

  @GetMapping("/api/dashboard/applications-to-follow-up")
  public DashboardModulePageResponse<DashboardApplicationItemResponse> getApplicationsToFollowUp(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) Integer offset) {
    return dashboardService.getApplicationsToFollowUp(currentUser.id(), offset);
  }

  @GetMapping("/api/dashboard/upcoming-interviews")
  public DashboardModulePageResponse<DashboardInterviewItemResponse> getUpcomingInterviews(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) Integer offset) {
    return dashboardService.getUpcomingInterviews(currentUser.id(), offset);
  }

  @GetMapping("/api/dashboard/interviews-to-follow-up")
  public DashboardModulePageResponse<DashboardInterviewItemResponse> getInterviewsToFollowUp(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) Integer offset) {
    return dashboardService.getInterviewsToFollowUp(currentUser.id(), offset);
  }
}
