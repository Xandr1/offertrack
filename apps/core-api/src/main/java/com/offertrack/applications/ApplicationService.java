package com.offertrack.applications;

import com.offertrack.applications.dto.ApplicationBoardColumnResponse;
import com.offertrack.applications.dto.ApplicationBoardResponse;
import com.offertrack.applications.dto.ApplicationListResponse;
import com.offertrack.applications.dto.ApplicationResponse;
import com.offertrack.applications.dto.ApplicationWithInterviewsResponse;
import com.offertrack.applications.dto.CreateApplicationInterviewItemRequest;
import com.offertrack.applications.dto.CreateApplicationRequest;
import com.offertrack.applications.dto.NextInterviewResponse;
import com.offertrack.applications.dto.ReplaceApplicationInterviewItemRequest;
import com.offertrack.applications.dto.ReplaceApplicationRequest;
import com.offertrack.applications.dto.UpdateApplicationStageRequest;
import com.offertrack.interviews.ApplicationInterview;
import com.offertrack.interviews.ApplicationInterviewRepository;
import com.offertrack.interviews.ApplicationInterviewResponseMapper;
import com.offertrack.interviews.InterviewNotFoundException;
import com.offertrack.interviews.InterviewStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {
  public static final int BOARD_COLUMN_PAGE_SIZE = 20;
  private static final int MAX_INTERVIEWS_PER_APPLICATION = 10;
  private static final List<ApplicationStage> BOARD_STAGE_ORDER =
      List.of(
          ApplicationStage.INITIAL,
          ApplicationStage.APPLIED,
          ApplicationStage.INTERVIEWING,
          ApplicationStage.OFFER,
          ApplicationStage.REJECTED);

  private final ApplicationRepository applicationRepository;
  private final ApplicationInterviewRepository applicationInterviewRepository;

  public ApplicationService(
      ApplicationRepository applicationRepository,
      ApplicationInterviewRepository applicationInterviewRepository) {
    this.applicationRepository = applicationRepository;
    this.applicationInterviewRepository = applicationInterviewRepository;
  }

  @Transactional
  public ApplicationWithInterviewsResponse create(UUID userId, CreateApplicationRequest request) {
    ApplicationStage stage = request.stage() != null ? request.stage() : ApplicationStage.INITIAL;
    List<CreateApplicationInterviewItemRequest> interviews =
        request.interviews() == null ? List.of() : request.interviews();

    validateInterviewCount(interviews.size());

    Application application = applicationRepository.create(userId, request, stage);

    for (var interviewRequest : interviews) {
      InterviewStatus status =
          interviewRequest.status() != null ? interviewRequest.status() : InterviewStatus.PLANNED;
      applicationInterviewRepository.create(
          application.id(),
          userId,
          interviewRequest.type(),
          status,
          interviewRequest.scheduledAt());
    }

    List<ApplicationInterview> storedInterviews =
        applicationInterviewRepository.listByApplicationForUser(application.id(), userId);
    NextInterviewResponse nextInterview = loadNextInterview(userId, application.id());

    return new ApplicationWithInterviewsResponse(
        ApplicationResponseMapper.toResponse(application, nextInterview),
        storedInterviews.stream().map(ApplicationInterviewResponseMapper::toResponse).toList());
  }

  public ApplicationListResponse list(UUID userId) {
    return list(userId, ApplicationListQuery.defaults());
  }

  public ApplicationListResponse list(UUID userId, ApplicationListQuery query) {
    ApplicationListPage page = applicationRepository.listByUser(userId, query);
    List<Application> applications = page.items();
    List<UUID> applicationIds = applications.stream().map(Application::id).toList();

    Map<UUID, ApplicationInterview> nextInterviewByApplicationId =
        applicationIds.isEmpty()
            ? Collections.emptyMap()
            : applicationInterviewRepository.findNextByApplicationIdsForUser(
                userId, applicationIds);

    List<ApplicationResponse> items =
        applications.stream()
            .map(
                application ->
                    ApplicationResponseMapper.toResponse(
                        application,
                        toNextInterviewResponse(
                            nextInterviewByApplicationId.get(application.id()))))
            .toList();

    return new ApplicationListResponse(
        items, page.page(), page.size(), page.totalItems(), page.totalPages());
  }

  public ApplicationBoardResponse board(UUID userId, ApplicationBoardQuery query) {
    Map<ApplicationStage, Long> totalCountByStage =
        applicationRepository.countByStageForUser(userId, query.search());
    Map<ApplicationStage, List<Application>> applicationsByStage =
        new EnumMap<>(ApplicationStage.class);
    List<Application> allApplications = new ArrayList<>();

    for (ApplicationStage stage : BOARD_STAGE_ORDER) {
      List<Application> applications =
          applicationRepository.listBoardColumn(
              userId,
              stage,
              query.search(),
              query.sort(),
              query.direction(),
              0,
              BOARD_COLUMN_PAGE_SIZE);
      applicationsByStage.put(stage, applications);
      allApplications.addAll(applications);
    }

    Map<UUID, ApplicationInterview> nextInterviewByApplicationId =
        loadNextInterviews(userId, allApplications);
    List<ApplicationBoardColumnResponse> columns =
        BOARD_STAGE_ORDER.stream()
            .map(
                stage ->
                    toBoardColumnResponse(
                        stage,
                        totalCountByStage.getOrDefault(stage, 0L),
                        applicationsByStage.getOrDefault(stage, List.of()),
                        0,
                        nextInterviewByApplicationId))
            .toList();

    return new ApplicationBoardResponse(columns);
  }

  public ApplicationBoardColumnResponse boardColumn(
      UUID userId, ApplicationStage stage, ApplicationBoardQuery query) {
    long totalCount = applicationRepository.countBoardColumn(userId, stage, query.search());
    List<Application> applications =
        applicationRepository.listBoardColumn(
            userId,
            stage,
            query.search(),
            query.sort(),
            query.direction(),
            query.offset(),
            BOARD_COLUMN_PAGE_SIZE);

    return toBoardColumnResponse(
        stage, totalCount, applications, query.offset(), loadNextInterviews(userId, applications));
  }

  public ApplicationResponse get(UUID userId, UUID applicationId) {
    Application application = ensureApplicationExistsForUser(userId, applicationId);
    NextInterviewResponse nextInterview = loadNextInterview(userId, applicationId);

    return ApplicationResponseMapper.toResponse(application, nextInterview);
  }

  public void delete(UUID userId, UUID applicationId) {
    boolean deleted = applicationRepository.delete(applicationId, userId);

    if (!deleted) {
      throw new ApplicationNotFoundException();
    }
  }

  public ApplicationResponse updateStage(
      UUID userId, UUID applicationId, UpdateApplicationStageRequest request) {
    Application application =
        applicationRepository
            .updateStage(applicationId, userId, request.stage())
            .orElseThrow(ApplicationNotFoundException::new);
    NextInterviewResponse nextInterview = loadNextInterview(userId, application.id());

    return ApplicationResponseMapper.toResponse(application, nextInterview);
  }

  @Transactional
  public ApplicationWithInterviewsResponse replace(
      UUID userId, UUID applicationId, ReplaceApplicationRequest request) {
    ensureApplicationExistsForUser(userId, applicationId);
    List<ReplaceApplicationInterviewItemRequest> interviews = request.interviews();
    ApplicationStage stage = request.stage() != null ? request.stage() : ApplicationStage.INITIAL;
    validateInterviewCount(interviews.size());

    List<ApplicationInterview> existingInterviews =
        applicationInterviewRepository.listByApplicationForUser(applicationId, userId);
    Map<UUID, ApplicationInterview> existingInterviewById = new HashMap<>();
    for (ApplicationInterview existingInterview : existingInterviews) {
      existingInterviewById.put(existingInterview.id(), existingInterview);
    }
    validateReplaceInterviewIds(interviews, existingInterviewById);

    Application application =
        applicationRepository
            .replace(applicationId, userId, request, stage)
            .orElseThrow(ApplicationNotFoundException::new);

    Set<UUID> requestedInterviewIds = new HashSet<>();
    for (ReplaceApplicationInterviewItemRequest interviewRequest : interviews) {
      if (interviewRequest.id() != null) {
        requestedInterviewIds.add(interviewRequest.id());
        applicationInterviewRepository
            .replace(
                applicationId,
                interviewRequest.id(),
                userId,
                interviewRequest.type(),
                interviewRequest.status(),
                interviewRequest.scheduledAt())
            .orElseThrow(InterviewNotFoundException::new);
        continue;
      }

      applicationInterviewRepository.create(
          applicationId,
          userId,
          interviewRequest.type(),
          interviewRequest.status(),
          interviewRequest.scheduledAt());
    }

    for (ApplicationInterview existingInterview : existingInterviews) {
      if (requestedInterviewIds.contains(existingInterview.id())) {
        continue;
      }

      applicationInterviewRepository.delete(applicationId, existingInterview.id(), userId);
    }

    List<ApplicationInterview> storedInterviews =
        applicationInterviewRepository.listByApplicationForUser(applicationId, userId);
    NextInterviewResponse nextInterview = loadNextInterview(userId, application.id());

    return new ApplicationWithInterviewsResponse(
        ApplicationResponseMapper.toResponse(application, nextInterview),
        storedInterviews.stream().map(ApplicationInterviewResponseMapper::toResponse).toList());
  }

  private Application ensureApplicationExistsForUser(UUID userId, UUID applicationId) {
    return applicationRepository
        .findByIdForUser(applicationId, userId)
        .orElseThrow(ApplicationNotFoundException::new);
  }

  private static void validateInterviewCount(int interviewCount) {
    if (interviewCount > MAX_INTERVIEWS_PER_APPLICATION) {
      throw new InvalidInterviewCountException(MAX_INTERVIEWS_PER_APPLICATION);
    }
  }

  private static void validateReplaceInterviewIds(
      List<ReplaceApplicationInterviewItemRequest> interviews,
      Map<UUID, ApplicationInterview> existingInterviewById) {
    Set<UUID> seenInterviewIds = new HashSet<>();
    List<UUID> duplicateInterviewIds = new ArrayList<>();

    for (ReplaceApplicationInterviewItemRequest interview : interviews) {
      UUID interviewId = interview.id();
      if (interviewId == null) {
        continue;
      }

      if (!seenInterviewIds.add(interviewId)) {
        duplicateInterviewIds.add(interviewId);
      }
    }

    if (!duplicateInterviewIds.isEmpty()) {
      throw new DuplicateInterviewIdsException();
    }

    for (UUID interviewId : seenInterviewIds) {
      if (existingInterviewById.containsKey(interviewId)) {
        continue;
      }

      throw new InterviewNotFoundException();
    }
  }

  private NextInterviewResponse loadNextInterview(UUID userId, UUID applicationId) {
    return applicationInterviewRepository
        .findNextByApplicationForUser(applicationId, userId)
        .map(ApplicationService::toNextInterviewResponse)
        .orElse(null);
  }

  private Map<UUID, ApplicationInterview> loadNextInterviews(
      UUID userId, List<Application> applications) {
    List<UUID> applicationIds = applications.stream().map(Application::id).toList();
    return applicationIds.isEmpty()
        ? Collections.emptyMap()
        : applicationInterviewRepository.findNextByApplicationIdsForUser(userId, applicationIds);
  }

  private static ApplicationBoardColumnResponse toBoardColumnResponse(
      ApplicationStage stage,
      long totalCount,
      List<Application> applications,
      int offset,
      Map<UUID, ApplicationInterview> nextInterviewByApplicationId) {
    List<ApplicationResponse> items =
        applications.stream()
            .map(
                application ->
                    ApplicationResponseMapper.toResponse(
                        application,
                        toNextInterviewResponse(
                            nextInterviewByApplicationId.get(application.id()))))
            .toList();
    int nextOffset = offset + items.size();

    return new ApplicationBoardColumnResponse(
        stage, totalCount, items, nextOffset, nextOffset < totalCount);
  }

  private static NextInterviewResponse toNextInterviewResponse(ApplicationInterview interview) {
    if (interview == null) {
      return null;
    }

    return new NextInterviewResponse(
        interview.id(), interview.type(), interview.status(), interview.scheduledAt());
  }
}
