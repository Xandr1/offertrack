package com.offertrack.dashboard;

import com.offertrack.dashboard.dto.DashboardRecentApplicationResponse;
import com.offertrack.dashboard.dto.DashboardSummaryResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  private static final int RECENT_APPLICATIONS_LIMIT = 5;
  private static final long NEEDS_ATTENTION_DAYS = 7;

  private final DashboardRepository dashboardRepository;

  public DashboardService(DashboardRepository dashboardRepository) {
    this.dashboardRepository = dashboardRepository;
  }

  public DashboardSummaryResponse getSummary(UUID userId) {
    OffsetDateTime staleBefore = OffsetDateTime.now().minusDays(NEEDS_ATTENTION_DAYS);
    DashboardSummaryCounts counts = dashboardRepository.getSummaryCounts(userId, staleBefore);

    List<DashboardRecentApplicationResponse> recentApplications =
        dashboardRepository.listRecentApplications(userId, RECENT_APPLICATIONS_LIMIT).stream()
            .map(
                application ->
                    new DashboardRecentApplicationResponse(
                        application.id(),
                        application.companyName(),
                        application.positionTitle(),
                        application.stage(),
                        application.updatedAt()))
            .toList();

    return new DashboardSummaryResponse(
        counts.activeProcesses(),
        counts.needsAttention(),
        counts.interviewing(),
        counts.offers(),
        counts.rejected(),
        recentApplications);
  }
}
