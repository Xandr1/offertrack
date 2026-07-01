package com.offertrack.applications.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertrack.applications.ApplicationStage;
import com.offertrack.applications.dto.ApplicationDraftInterviewResponse;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import com.offertrack.interviews.InterviewStatus;
import com.offertrack.interviews.InterviewType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisAiDraftCacheServiceTest {
  private static final String KEY = "offertrack:ai-draft:v1:url-sha256:abc";

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private AiDraftCacheProperties properties;
  private RedisAiDraftCacheService service;

  @BeforeEach
  void setUp() {
    properties = new AiDraftCacheProperties();
    properties.setTtl(Duration.ofHours(24));
    service = new RedisAiDraftCacheService(redisTemplate, objectMapper, properties);
  }

  @Test
  void serializesDraftWithConfiguredTtl() throws Exception {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    ApplicationDraftResponse draft = sampleDraft();
    String expectedJson = objectMapper.writeValueAsString(draft);

    service.put(KEY, draft);

    verify(valueOperations).set(KEY, expectedJson, Duration.ofHours(24));
  }

  @Test
  void deserializesValidDraftIncludingInterviews() throws Exception {
    ApplicationDraftResponse draft = sampleDraft();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(KEY)).thenReturn(objectMapper.writeValueAsString(draft));

    assertThat(service.get(KEY)).contains(draft);
  }

  @Test
  void rejectsMalformedJson() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(KEY)).thenReturn("not-json");

    assertThatThrownBy(() -> service.get(KEY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cached AI draft could not be deserialized");
  }

  @Test
  void rejectsStructurallyInvalidDraft() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(KEY))
        .thenReturn(
            """
            {
              "jobUrl": "https://example.com/jobs/123",
              "stage": "initial",
              "interviews": null,
              "warnings": []
            }
            """);

    assertThatThrownBy(() -> service.get(KEY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cached AI draft has an invalid structure");
  }

  @Test
  void propagatesRedisOperationFailureToOrchestratorBoundary() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(KEY)).thenThrow(new RedisConnectionFailureException("unavailable"));

    assertThatThrownBy(() -> service.get(KEY)).isInstanceOf(RedisConnectionFailureException.class);
  }

  @Test
  void disabledCachePerformsNoRedisOperations() {
    properties.setEnabled(false);

    assertThat(service.get(KEY)).isEmpty();
    service.put(KEY, sampleDraft());

    verifyNoInteractions(redisTemplate);
  }

  private static ApplicationDraftResponse sampleDraft() {
    return new ApplicationDraftResponse(
        "Acme",
        "Backend Engineer",
        "https://example.com/jobs/123",
        "Remote",
        "remote",
        ApplicationStage.INITIAL,
        "Role summary",
        List.of(
            new ApplicationDraftInterviewResponse(
                InterviewType.TECHNICAL,
                InterviewStatus.INITIAL,
                OffsetDateTime.parse("2026-07-01T10:00:00Z"))),
        List.of("Location was inferred."));
  }
}
