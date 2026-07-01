package com.offertrack.applications.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertrack.applications.ApplicationStage;
import com.offertrack.applications.dto.ApplicationDraftInterviewResponse;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import com.offertrack.interviews.InterviewStatus;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisAiDraftCacheService implements AiDraftCacheService {
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final AiDraftCacheProperties properties;

  public RedisAiDraftCacheService(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      AiDraftCacheProperties properties) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public Optional<ApplicationDraftResponse> get(String key) {
    if (!properties.isEnabled()) {
      return Optional.empty();
    }

    String json = redisTemplate.opsForValue().get(key);
    if (json == null) {
      return Optional.empty();
    }

    try {
      ApplicationDraftResponse draft = objectMapper.readValue(json, ApplicationDraftResponse.class);
      validate(draft);
      return Optional.of(draft);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Cached AI draft could not be deserialized", exception);
    }
  }

  @Override
  public void put(String key, ApplicationDraftResponse draft) {
    if (!properties.isEnabled()) {
      return;
    }

    try {
      String json = objectMapper.writeValueAsString(draft);
      redisTemplate.opsForValue().set(key, json, properties.getTtl());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("AI draft could not be serialized for caching", exception);
    }
  }

  private static void validate(ApplicationDraftResponse draft) {
    if (draft == null
        || draft.jobUrl() == null
        || draft.jobUrl().isBlank()
        || draft.stage() != ApplicationStage.INITIAL
        || draft.interviews() == null
        || draft.warnings() == null
        || draft.warnings().stream().anyMatch(value -> value == null)) {
      throw new IllegalStateException("Cached AI draft has an invalid structure");
    }

    for (ApplicationDraftInterviewResponse interview : draft.interviews()) {
      if (interview == null
          || interview.type() == null
          || interview.status() != InterviewStatus.INITIAL) {
        throw new IllegalStateException("Cached AI draft has an invalid interview structure");
      }
    }
  }
}
