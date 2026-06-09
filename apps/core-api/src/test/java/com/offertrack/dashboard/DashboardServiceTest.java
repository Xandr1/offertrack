package com.offertrack.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offertrack.applications.ApplicationStage;
import com.offertrack.dashboard.dto.DashboardSummaryResponse;
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

  private DashboardService dashboardService;

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(Instant.parse("2026-06-06T12:00:00Z"), ZoneOffset.UTC);
    dashboardService = new DashboardService(dashboardRepository, settingsService, fixedClock);
  }

  @Test
  void getSummaryBuildsResponseAndUsesClockBasedCutoffs() {
    UUID userId = UUID.randomUUID();

    DashboardSummaryCounts counts = new DashboardSummaryCounts(6, 3, 1, 4);
    List<DashboardApplicationItem> draftsToApply =
        List.of(
            new DashboardApplicationItem(
                UUID.randomUUID(),
                "Acme",
                "Backend Engineer",
                ApplicationStage.INITIAL,
                "https://example.com/job",
                "Remote",
                "remote",
                null,
                OffsetDateTime.parse("2026-05-01T10:15:00Z")),
            new DashboardApplicationItem(
                UUID.randomUUID(),
                "Globex",
                "Platform Engineer",
                ApplicationStage.INITIAL,
                null,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-04-28T08:30:00Z")));
    List<DashboardApplicationItem> applicationsToFollowUp =
        List.of(
            new DashboardApplicationItem(
                UUID.randomUUID(),
                "Initech",
                "Java Engineer",
                ApplicationStage.APPLIED,
                null,
                "Warsaw",
                "hybrid",
                OffsetDateTime.parse("2026-05-20T08:00:00Z"),
                OffsetDateTime.parse("2026-05-21T08:00:00Z")));
    List<DashboardInterviewItem> upcomingInterviews =
        List.of(
            new DashboardInterviewItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Umbrella",
                "Frontend Engineer",
                null,
                null,
                null,
                OffsetDateTime.parse("2026-06-08T09:00:00Z"),
                InterviewType.TECHNICAL,
                InterviewStatus.SCHEDULED));
    List<DashboardInterviewItem> interviewsToFollowUp =
        List.of(
            new DashboardInterviewItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Soylent",
                "Platform Engineer",
                null,
                null,
                null,
                OffsetDateTime.parse("2026-06-02T09:00:00Z"),
                InterviewType.HR,
                InterviewStatus.COMPLETED));

    when(settingsService.getSettings(userId)).thenReturn(UserSettings.defaultForUser(userId));
    when(dashboardRepository.getSummaryCounts(userId)).thenReturn(counts);
    when(dashboardRepository.countDraftsToApply(userId)).thenReturn(2L);
    when(dashboardRepository.countApplicationsToFollowUp(userId, NOW.minusDays(7), NOW))
        .thenReturn(3L);
    when(dashboardRepository.countUpcomingInterviews(userId, NOW, NOW.plusDays(7))).thenReturn(4L);
    when(dashboardRepository.countInterviewsToFollowUp(userId, NOW.minusDays(2))).thenReturn(5L);
    when(dashboardRepository.listDraftsToApply(userId, 3)).thenReturn(draftsToApply);
    when(dashboardRepository.listApplicationsToFollowUp(userId, NOW.minusDays(7), NOW, 3))
        .thenReturn(applicationsToFollowUp);
    when(dashboardRepository.listUpcomingInterviews(userId, NOW, NOW.plusDays(7), 3))
        .thenReturn(upcomingInterviews);
    when(dashboardRepository.listInterviewsToFollowUp(userId, NOW.minusDays(2), 3))
        .thenReturn(interviewsToFollowUp);

    DashboardSummaryResponse response = dashboardService.getSummary(userId);

    assertThat(response.activeProcesses()).isEqualTo(6);
    assertThat(response.needsAttention()).isEqualTo(14);
    assertThat(response.interviewing()).isEqualTo(3);
    assertThat(response.offers()).isEqualTo(1);
    assertThat(response.rejected()).isEqualTo(4);
    assertThat(response.draftsToApplyCount()).isEqualTo(2);
    assertThat(response.applicationsToFollowUpCount()).isEqualTo(3);
    assertThat(response.upcomingInterviewsCount()).isEqualTo(4);
    assertThat(response.interviewsToFollowUpCount()).isEqualTo(5);
    assertThat(response.draftsToApply()).hasSize(2);
    assertThat(response.draftsToApply().getFirst().stage()).isEqualTo(ApplicationStage.INITIAL);
    assertThat(response.applicationsToFollowUp()).hasSize(1);
    assertThat(response.upcomingInterviews().getFirst().interviewType())
        .isEqualTo(InterviewType.TECHNICAL);
    assertThat(response.interviewsToFollowUp().getFirst().status())
        .isEqualTo(InterviewStatus.COMPLETED);

    verify(dashboardRepository).countApplicationsToFollowUp(userId, NOW.minusDays(7), NOW);
    verify(dashboardRepository).countUpcomingInterviews(userId, NOW, NOW.plusDays(7));
    verify(dashboardRepository).countInterviewsToFollowUp(userId, NOW.minusDays(2));
    verify(dashboardRepository).listDraftsToApply(userId, 3);
    verify(dashboardRepository).listApplicationsToFollowUp(userId, NOW.minusDays(7), NOW, 3);
    verify(dashboardRepository).listUpcomingInterviews(userId, NOW, NOW.plusDays(7), 3);
    verify(dashboardRepository).listInterviewsToFollowUp(userId, NOW.minusDays(2), 3);
  }

  @Test
  void getSummaryUsesCustomSettingsForClockBasedCutoffs() {
    UUID userId = UUID.randomUUID();

    when(settingsService.getSettings(userId))
        .thenReturn(new UserSettings(userId, 14, 3, 5, "Platform Engineer"));
    when(dashboardRepository.getSummaryCounts(userId))
        .thenReturn(new DashboardSummaryCounts(0, 0, 0, 0));
    when(dashboardRepository.listDraftsToApply(userId, 3)).thenReturn(List.of());
    when(dashboardRepository.listApplicationsToFollowUp(userId, NOW.minusDays(14), NOW, 3))
        .thenReturn(List.of());
    when(dashboardRepository.listUpcomingInterviews(userId, NOW, NOW.plusDays(3), 3))
        .thenReturn(List.of());
    when(dashboardRepository.listInterviewsToFollowUp(userId, NOW.minusDays(5), 3))
        .thenReturn(List.of());

    dashboardService.getSummary(userId);

    verify(settingsService).getSettings(userId);
    verify(dashboardRepository).countApplicationsToFollowUp(userId, NOW.minusDays(14), NOW);
    verify(dashboardRepository).countUpcomingInterviews(userId, NOW, NOW.plusDays(3));
    verify(dashboardRepository).countInterviewsToFollowUp(userId, NOW.minusDays(5));
    verify(dashboardRepository).listApplicationsToFollowUp(userId, NOW.minusDays(14), NOW, 3);
    verify(dashboardRepository).listUpcomingInterviews(userId, NOW, NOW.plusDays(3), 3);
    verify(dashboardRepository).listInterviewsToFollowUp(userId, NOW.minusDays(5), 3);
  }
}
