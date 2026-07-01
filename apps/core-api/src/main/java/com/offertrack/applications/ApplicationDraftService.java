package com.offertrack.applications;

import com.offertrack.applications.cache.AiDraftCacheKey;
import com.offertrack.applications.cache.AiDraftCacheKeyFactory;
import com.offertrack.applications.cache.AiDraftCacheProperties;
import com.offertrack.applications.cache.AiDraftCacheService;
import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ApplicationDraftService {
  private static final Logger log = LoggerFactory.getLogger(ApplicationDraftService.class);

  private final AiServiceClient aiServiceClient;
  private final AiDraftCacheService cacheService;
  private final AiDraftCacheKeyFactory cacheKeyFactory;
  private final AiDraftCacheProperties cacheProperties;

  public ApplicationDraftService(
      AiServiceClient aiServiceClient,
      AiDraftCacheService cacheService,
      AiDraftCacheKeyFactory cacheKeyFactory,
      AiDraftCacheProperties cacheProperties) {
    this.aiServiceClient = aiServiceClient;
    this.cacheService = cacheService;
    this.cacheKeyFactory = cacheKeyFactory;
    this.cacheProperties = cacheProperties;
  }

  public ApplicationDraftResponse createDraft(ApplicationDraftRequest request) {
    if (!cacheProperties.isEnabled()) {
      return aiServiceClient.parseJob(request);
    }

    AiDraftCacheKey cacheKey;
    try {
      cacheKey = cacheKeyFactory.create(request.jobUrl());
    } catch (RuntimeException exception) {
      log.warn(
          "ai_draft_cache_read_failed reason=key_generation error_type={}",
          exception.getClass().getSimpleName());
      return aiServiceClient.parseJob(request);
    }

    Optional<ApplicationDraftResponse> cachedDraft;
    try {
      cachedDraft = cacheService.get(cacheKey.redisKey());
    } catch (RuntimeException exception) {
      log.warn(
          "ai_draft_cache_read_failed url_host={} cache_key_suffix={} error_type={}",
          cacheKey.host(),
          cacheKey.hashSuffix(),
          exception.getClass().getSimpleName());
      cachedDraft = Optional.empty();
    }

    if (cachedDraft.isPresent()) {
      log.info(
          "ai_draft_cache_hit url_host={} cache_key_suffix={}",
          cacheKey.host(),
          cacheKey.hashSuffix());
      return withJobUrl(cachedDraft.orElseThrow(), request.jobUrl());
    }

    log.info(
        "ai_draft_cache_miss url_host={} cache_key_suffix={}",
        cacheKey.host(),
        cacheKey.hashSuffix());
    ApplicationDraftResponse draft = aiServiceClient.parseJob(request);

    try {
      cacheService.put(cacheKey.redisKey(), withJobUrl(draft, cacheKey.normalizedUrl()));
    } catch (RuntimeException exception) {
      log.warn(
          "ai_draft_cache_write_failed url_host={} cache_key_suffix={} error_type={}",
          cacheKey.host(),
          cacheKey.hashSuffix(),
          exception.getClass().getSimpleName());
    }

    return draft;
  }

  private static ApplicationDraftResponse withJobUrl(
      ApplicationDraftResponse draft, String jobUrl) {
    return new ApplicationDraftResponse(
        draft.companyName(),
        draft.positionTitle(),
        jobUrl,
        draft.location(),
        draft.workMode(),
        draft.stage(),
        draft.notes(),
        draft.interviews(),
        draft.warnings());
  }
}
