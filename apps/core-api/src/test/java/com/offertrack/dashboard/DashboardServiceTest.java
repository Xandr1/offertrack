package com.offertrack.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offertrack.applications.ApplicationStage;
import com.offertrack.dashboard.dto.DashboardSummaryResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
  @Mock private DashboardRepository dashboardRepository;

  @InjectMocks private DashboardService dashboardService;

  @Test
  void getSummaryBuildsResponseAndUsesSevenDayCutoff() {
    UUID userId = UUID.randomUUID();
    OffsetDateTime expectedCutoffLowerBound = OffsetDateTime.now().minusDays(7).minusSeconds(2);
    OffsetDateTime expectedCutoffUpperBound = OffsetDateTime.now().minusDays(7).plusSeconds(2);

    DashboardSummaryCounts counts = new DashboardSummaryCounts(6, 2, 3, 1, 4);
    List<DashboardRecentApplication> recentApplications =
        List.of(
            new DashboardRecentApplication(
                UUID.randomUUID(),
                "Acme",
                "Backend Engineer",
                ApplicationStage.INTERVIEWING,
                OffsetDateTime.parse("2026-05-01T10:15:00Z")),
            new DashboardRecentApplication(
                UUID.randomUUID(),
                "Globex",
                "Platform Engineer",
                ApplicationStage.OFFER,
                OffsetDateTime.parse("2026-04-28T08:30:00Z")));

    ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    when(dashboardRepository.getSummaryCounts(eq(userId), cutoffCaptor.capture()))
        .thenReturn(counts);
    when(dashboardRepository.listRecentApplications(userId, 5)).thenReturn(recentApplications);

    DashboardSummaryResponse response = dashboardService.getSummary(userId);

    assertThat(cutoffCaptor.getValue())
        .isAfterOrEqualTo(expectedCutoffLowerBound)
        .isBeforeOrEqualTo(expectedCutoffUpperBound);
    assertThat(response.activeProcesses()).isEqualTo(6);
    assertThat(response.needsAttention()).isEqualTo(2);
    assertThat(response.interviewing()).isEqualTo(3);
    assertThat(response.offers()).isEqualTo(1);
    assertThat(response.rejected()).isEqualTo(4);
    assertThat(response.recentApplications()).hasSize(2);
    assertThat(response.recentApplications().getFirst().stage())
        .isEqualTo(ApplicationStage.INTERVIEWING);
    assertThat(response.recentApplications().get(1).stage()).isEqualTo(ApplicationStage.OFFER);

    verify(dashboardRepository).listRecentApplications(userId, 5);
  }
}
