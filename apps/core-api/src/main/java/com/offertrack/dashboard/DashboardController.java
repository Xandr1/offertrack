package com.offertrack.dashboard;

import com.offertrack.auth.CurrentUser;
import com.offertrack.dashboard.dto.DashboardSummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
}
