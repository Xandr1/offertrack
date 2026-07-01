package com.offertrack.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offertrack.applications.ApplicationStage;
import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import com.offertrack.settings.SettingsService;
import com.offertrack.settings.UserSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-06T12:00:00Z");
  @Mock private DashboardRepository dashboardRepository;
  @Mock private SettingsService settingsService;
  private DashboardService service;

  @BeforeEach
  void setUp() {
    service =
        new DashboardService(
            dashboardRepository,
            settingsService,
            Clock.fixed(Instant.parse("2026-06-06T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void summaryReturnsThreeFirstPagesAndNeedsAttentionTotal() {
    UUID userId = UUID.randomUUID();
    UserSettings settings = UserSettings.defaultForUser(userId);
    DashboardApplicationItem application =
        new DashboardApplicationItem(
            UUID.randomUUID(),
            "Acme",
            "Engineer",
            ApplicationStage.APPLIED,
            null,
            null,
            null,
            NOW.minusDays(8),
            NOW.minusDays(9),
            NOW);
    DashboardInterviewItem interview =
        new DashboardInterviewItem(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Globex",
            "Engineer",
            null,
            null,
            null,
            NOW.plusDays(1),
            InterviewType.TECHNICAL,
            InterviewStatus.SCHEDULED);

    when(settingsService.getSettings(userId)).thenReturn(settings);
    when(dashboardRepository.getSummaryCounts(userId))
        .thenReturn(new DashboardSummaryCounts(6, 3, 1, 4));
    when(dashboardRepository.countApplicationsToFollowUp(userId, NOW.minusDays(7))).thenReturn(3L);
    when(dashboardRepository.listApplicationsToFollowUp(userId, NOW.minusDays(7), 0, 10))
        .thenReturn(List.of(application));
    when(dashboardRepository.countUpcomingInterviews(userId, NOW, NOW.plusDays(7))).thenReturn(4L);
    when(dashboardRepository.listUpcomingInterviews(userId, NOW, NOW.plusDays(7), 0, 10))
        .thenReturn(List.of(interview));
    when(dashboardRepository.countInterviewsToFollowUp(userId, NOW, NOW.minusDays(2)))
        .thenReturn(5L);
    when(dashboardRepository.listInterviewsToFollowUp(userId, NOW, NOW.minusDays(2), 0, 10))
        .thenReturn(List.of());

    var response = service.getSummary(userId);

    assertThat(response.needsAttention()).isEqualTo(12);
    assertThat(response.applicationsToFollowUp().totalCount()).isEqualTo(3);
    assertThat(response.applicationsToFollowUp().items()).hasSize(1);
    assertThat(response.applicationsToFollowUp().items().get(0).createdAt())
        .isEqualTo(NOW.minusDays(9));
    assertThat(response.upcomingInterviews().items()).hasSize(1);
    assertThat(response.interviewsToFollowUp().totalCount()).isEqualTo(5);
  }

  @Test
  void loadMoreUsesIndependentOffsetAndConfiguredCutoff() {
    UUID userId = UUID.randomUUID();
    UserSettings settings = new UserSettings(userId, 14, 3, 5, null);
    when(settingsService.getSettings(userId)).thenReturn(settings);
    when(dashboardRepository.countApplicationsToFollowUp(userId, NOW.minusDays(14)))
        .thenReturn(22L);
    when(dashboardRepository.listApplicationsToFollowUp(userId, NOW.minusDays(14), 10, 10))
        .thenReturn(List.of());

    var page = service.getApplicationsToFollowUp(userId, 10);

    assertThat(page.totalCount()).isEqualTo(22);
    assertThat(page.nextOffset()).isEqualTo(10);
    assertThat(page.hasMore()).isTrue();
    verify(dashboardRepository).listApplicationsToFollowUp(userId, NOW.minusDays(14), 10, 10);
  }
}
