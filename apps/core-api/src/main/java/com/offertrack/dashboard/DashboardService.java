package com.offertrack.dashboard;

import com.offertrack.dashboard.dto.DashboardApplicationItemResponse;
import com.offertrack.dashboard.dto.DashboardInterviewItemResponse;
import com.offertrack.dashboard.dto.DashboardModulePageResponse;
import com.offertrack.dashboard.dto.DashboardSummaryResponse;
import com.offertrack.settings.SettingsService;
import com.offertrack.settings.UserSettings;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService {
  public static final int DASHBOARD_MODULE_PAGE_SIZE = 10;

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
    DashboardSummaryCounts counts = dashboardRepository.getSummaryCounts(userId);

    DashboardModulePageResponse<DashboardApplicationItemResponse> applications =
        applicationsToFollowUp(userId, settings, 0, now);
    DashboardModulePageResponse<DashboardInterviewItemResponse> upcoming =
        upcomingInterviews(userId, settings, 0, now);
    DashboardModulePageResponse<DashboardInterviewItemResponse> interviews =
        interviewsToFollowUp(userId, settings, 0, now);

    return new DashboardSummaryResponse(
        counts.activeProcesses(),
        applications.totalCount() + upcoming.totalCount() + interviews.totalCount(),
        counts.interviewing(),
        counts.offers(),
        counts.rejected(),
        settings.followUpAfterApplyingDays(),
        settings.upcomingInterviewDays(),
        settings.followUpAfterInterviewDays(),
        applications,
        upcoming,
        interviews);
  }

  public DashboardModulePageResponse<DashboardApplicationItemResponse> getApplicationsToFollowUp(
      UUID userId, Integer offset) {
    return applicationsToFollowUp(
        userId,
        settingsService.getSettings(userId),
        validateOffset(offset),
        OffsetDateTime.now(clock));
  }

  public DashboardModulePageResponse<DashboardInterviewItemResponse> getUpcomingInterviews(
      UUID userId, Integer offset) {
    return upcomingInterviews(
        userId,
        settingsService.getSettings(userId),
        validateOffset(offset),
        OffsetDateTime.now(clock));
  }

  public DashboardModulePageResponse<DashboardInterviewItemResponse> getInterviewsToFollowUp(
      UUID userId, Integer offset) {
    return interviewsToFollowUp(
        userId,
        settingsService.getSettings(userId),
        validateOffset(offset),
        OffsetDateTime.now(clock));
  }

  private DashboardModulePageResponse<DashboardApplicationItemResponse> applicationsToFollowUp(
      UUID userId, UserSettings settings, int offset, OffsetDateTime now) {
    OffsetDateTime cutoff = now.minusDays(settings.followUpAfterApplyingDays());
    long total = dashboardRepository.countApplicationsToFollowUp(userId, cutoff);
    List<DashboardApplicationItemResponse> items =
        dashboardRepository
            .listApplicationsToFollowUp(userId, cutoff, offset, DASHBOARD_MODULE_PAGE_SIZE)
            .stream()
            .map(DashboardService::toApplicationResponse)
            .toList();
    return page(total, items, offset);
  }

  private DashboardModulePageResponse<DashboardInterviewItemResponse> upcomingInterviews(
      UUID userId, UserSettings settings, int offset, OffsetDateTime now) {
    OffsetDateTime before = now.plusDays(settings.upcomingInterviewDays());
    long total = dashboardRepository.countUpcomingInterviews(userId, now, before);
    List<DashboardInterviewItemResponse> items =
        dashboardRepository
            .listUpcomingInterviews(userId, now, before, offset, DASHBOARD_MODULE_PAGE_SIZE)
            .stream()
            .map(DashboardService::toInterviewResponse)
            .toList();
    return page(total, items, offset);
  }

  private DashboardModulePageResponse<DashboardInterviewItemResponse> interviewsToFollowUp(
      UUID userId, UserSettings settings, int offset, OffsetDateTime now) {
    OffsetDateTime cutoff = now.minusDays(settings.followUpAfterInterviewDays());
    long total = dashboardRepository.countInterviewsToFollowUp(userId, now, cutoff);
    List<DashboardInterviewItemResponse> items =
        dashboardRepository
            .listInterviewsToFollowUp(userId, now, cutoff, offset, DASHBOARD_MODULE_PAGE_SIZE)
            .stream()
            .map(DashboardService::toInterviewResponse)
            .toList();
    return page(total, items, offset);
  }

  private static int validateOffset(Integer offset) {
    int parsed = offset == null ? 0 : offset;
    if (parsed < 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Offset must be greater than or equal to 0.");
    }
    return parsed;
  }

  private static <T> DashboardModulePageResponse<T> page(long total, List<T> items, int offset) {
    int nextOffset = offset + items.size();
    return new DashboardModulePageResponse<>(total, items, nextOffset, nextOffset < total);
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
        application.createdAt(),
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
