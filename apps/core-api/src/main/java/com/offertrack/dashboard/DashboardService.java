package com.offertrack.dashboard;

import com.offertrack.dashboard.dto.DashboardApplicationItemResponse;
import com.offertrack.dashboard.dto.DashboardInterviewItemResponse;
import com.offertrack.dashboard.dto.DashboardSummaryResponse;
import com.offertrack.settings.SettingsService;
import com.offertrack.settings.UserSettings;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  static final int DASHBOARD_MODULE_LIMIT = 3;

  private final DashboardRepository dashboardRepository;
  private final SettingsService settingsService;
  private final Clock clock;

  public DashboardService(
      DashboardRepository dashboardRepository, SettingsService settingsService, Clock clock) {
    this.dashboardRepository = dashboardRepository;
    this.settingsService = settingsService;
    this.clock = clock;
  }

  public DashboardSummaryResponse getSummary(UUID userId) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    UserSettings settings = settingsService.getSettings(userId);
    OffsetDateTime applicationFollowUpBefore = now.minusDays(settings.followUpAfterApplyingDays());
    OffsetDateTime upcomingInterviewBefore = now.plusDays(settings.upcomingInterviewDays());
    OffsetDateTime interviewFollowUpBefore = now.minusDays(settings.followUpAfterInterviewDays());

    DashboardSummaryCounts counts = dashboardRepository.getSummaryCounts(userId);

    long draftsToApplyCount = dashboardRepository.countDraftsToApply(userId);
    long applicationsToFollowUpCount =
        dashboardRepository.countApplicationsToFollowUp(userId, applicationFollowUpBefore, now);
    long upcomingInterviewsCount =
        dashboardRepository.countUpcomingInterviews(userId, now, upcomingInterviewBefore);
    long interviewsToFollowUpCount =
        dashboardRepository.countInterviewsToFollowUp(userId, interviewFollowUpBefore);
    long needsAttention =
        draftsToApplyCount
            + applicationsToFollowUpCount
            + upcomingInterviewsCount
            + interviewsToFollowUpCount;

    List<DashboardApplicationItemResponse> draftsToApply =
        dashboardRepository.listDraftsToApply(userId, DASHBOARD_MODULE_LIMIT).stream()
            .map(DashboardService::toApplicationResponse)
            .toList();

    List<DashboardApplicationItemResponse> applicationsToFollowUp =
        dashboardRepository
            .listApplicationsToFollowUp(
                userId, applicationFollowUpBefore, now, DASHBOARD_MODULE_LIMIT)
            .stream()
            .map(DashboardService::toApplicationResponse)
            .toList();

    List<DashboardInterviewItemResponse> upcomingInterviews =
        dashboardRepository
            .listUpcomingInterviews(userId, now, upcomingInterviewBefore, DASHBOARD_MODULE_LIMIT)
            .stream()
            .map(DashboardService::toInterviewResponse)
            .toList();

    List<DashboardInterviewItemResponse> interviewsToFollowUp =
        dashboardRepository
            .listInterviewsToFollowUp(userId, interviewFollowUpBefore, DASHBOARD_MODULE_LIMIT)
            .stream()
            .map(DashboardService::toInterviewResponse)
            .toList();

    return new DashboardSummaryResponse(
        counts.activeProcesses(),
        needsAttention,
        counts.interviewing(),
        counts.offers(),
        counts.rejected(),
        draftsToApplyCount,
        applicationsToFollowUpCount,
        upcomingInterviewsCount,
        interviewsToFollowUpCount,
        settings.followUpAfterApplyingDays(),
        settings.upcomingInterviewDays(),
        settings.followUpAfterInterviewDays(),
        draftsToApply,
        applicationsToFollowUp,
        upcomingInterviews,
        interviewsToFollowUp);
  }

  private static DashboardApplicationItemResponse toApplicationResponse(
      DashboardApplicationItem application) {
    return new DashboardApplicationItemResponse(
        application.applicationId(),
        application.companyName(),
        application.positionTitle(),
        application.stage(),
        application.jobUrl(),
        application.location(),
        application.workMode(),
        application.appliedAt(),
        application.updatedAt());
  }

  private static DashboardInterviewItemResponse toInterviewResponse(
      DashboardInterviewItem interview) {
    return new DashboardInterviewItemResponse(
        interview.applicationId(),
        interview.interviewId(),
        interview.companyName(),
        interview.positionTitle(),
        interview.jobUrl(),
        interview.location(),
        interview.workMode(),
        interview.scheduledAt(),
        interview.interviewType(),
        interview.status());
  }
}
