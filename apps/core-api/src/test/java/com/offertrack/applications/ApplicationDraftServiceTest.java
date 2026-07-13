package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.offertrack.applications.cache.AiDraftCacheKey;
import com.offertrack.applications.cache.AiDraftCacheKeyFactory;
import com.offertrack.applications.cache.AiDraftCacheProperties;
import com.offertrack.applications.cache.AiDraftCacheService;
import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ApplicationDraftServiceTest {
  private static final String REQUEST_URL =
      "https://EXAMPLE.com/jobs/123?utm_source=linkedin&jobId=42";
  private static final String NORMALIZED_URL = "https://example.com/jobs/123?jobId=42";
  private static final String CACHE_KEY = "offertrack:ai-draft:v1:url-sha256:abc";
  private static final AiDraftCacheKey CACHE_KEY_VALUE =
      new AiDraftCacheKey(CACHE_KEY, NORMALIZED_URL, "example.com", "abc");

  @Mock private AiServiceClient aiServiceClient;
  @Mock private AiDraftCacheService cacheService;
  @Mock private AiDraftCacheKeyFactory cacheKeyFactory;

  private AiDraftCacheProperties properties;
  private ApplicationDraftService service;
  private ApplicationDraftRequest request;

  @BeforeEach
  void setUp() {
    properties = new AiDraftCacheProperties();
    service =
        new ApplicationDraftService(aiServiceClient, cacheService, cacheKeyFactory, properties);
    request = new ApplicationDraftRequest(REQUEST_URL);
  }

  @Test
  void cacheHitReturnsCachedDraftWithCurrentRequestUrlWithoutCallingAi() {
    when(cacheKeyFactory.create(REQUEST_URL)).thenReturn(CACHE_KEY_VALUE);
    when(cacheService.get(CACHE_KEY)).thenReturn(Optional.of(sampleDraft(NORMALIZED_URL)));

    ApplicationDraftResponse result = service.createDraft(request);

    assertThat(result.jobUrl()).isEqualTo(REQUEST_URL);
    assertThat(result.companyName()).isEqualTo("Acme");
    verifyNoInteractions(aiServiceClient);
    verify(cacheService, never()).put(any(), any());
  }

  @Test
  void cacheMissCallsAiAndWritesSanitizedCopy() {
    ApplicationDraftResponse freshDraft = sampleDraft(REQUEST_URL);
    when(cacheKeyFactory.create(REQUEST_URL)).thenReturn(CACHE_KEY_VALUE);
    when(cacheService.get(CACHE_KEY)).thenReturn(Optional.empty());
    when(aiServiceClient.parseJob(request)).thenReturn(freshDraft);
    ArgumentCaptor<ApplicationDraftResponse> cachedDraft =
        ArgumentCaptor.forClass(ApplicationDraftResponse.class);

    ApplicationDraftResponse result = service.createDraft(request);

    assertThat(result).isSameAs(freshDraft);
    verify(cacheService).put(org.mockito.ArgumentMatchers.eq(CACHE_KEY), cachedDraft.capture());
    assertThat(cachedDraft.getValue().jobUrl()).isEqualTo(NORMALIZED_URL);
  }

  @Test
  void aiFailureIsNotCached() {
    when(cacheKeyFactory.create(REQUEST_URL)).thenReturn(CACHE_KEY_VALUE);
    when(cacheService.get(CACHE_KEY)).thenReturn(Optional.empty());
    when(aiServiceClient.parseJob(request)).thenThrow(new AiServiceTimeoutException());

    assertThatThrownBy(() -> service.createDraft(request))
        .isInstanceOf(AiServiceTimeoutException.class);
    verify(cacheService, never()).put(any(), any());
  }

  @Test
  void cacheReadFailureFallsBackToAi() {
    ApplicationDraftResponse freshDraft = sampleDraft(REQUEST_URL);
    when(cacheKeyFactory.create(REQUEST_URL)).thenReturn(CACHE_KEY_VALUE);
    when(cacheService.get(CACHE_KEY)).thenThrow(new IllegalStateException("unavailable"));
    when(aiServiceClient.parseJob(request)).thenReturn(freshDraft);

    assertThat(service.createDraft(request)).isSameAs(freshDraft);
    verify(cacheService).put(any(), any());
  }

  @Test
  void cacheWriteFailureDoesNotReplaceSuccessfulResponse() {
    ApplicationDraftResponse freshDraft = sampleDraft(REQUEST_URL);
    when(cacheKeyFactory.create(REQUEST_URL)).thenReturn(CACHE_KEY_VALUE);
    when(cacheService.get(CACHE_KEY)).thenReturn(Optional.empty());
    when(aiServiceClient.parseJob(request)).thenReturn(freshDraft);
    org.mockito.Mockito.doThrow(new IllegalStateException("unavailable"))
        .when(cacheService)
        .put(any(), any());

    assertThat(service.createDraft(request)).isSameAs(freshDraft);
  }

  @Test
  void keyGenerationFailureBypassesCache() {
    ApplicationDraftResponse freshDraft = sampleDraft(REQUEST_URL);
    when(cacheKeyFactory.create(REQUEST_URL)).thenThrow(new IllegalArgumentException("invalid"));
    when(aiServiceClient.parseJob(request)).thenReturn(freshDraft);

    assertThat(service.createDraft(request)).isSameAs(freshDraft);
    verifyNoInteractions(cacheService);
  }

  @Test
  void disabledCacheBypassesKeyGenerationAndRedis() {
    properties.setEnabled(false);
    ApplicationDraftResponse freshDraft = sampleDraft(REQUEST_URL);
    when(aiServiceClient.parseJob(request)).thenReturn(freshDraft);

    assertThat(service.createDraft(request)).isSameAs(freshDraft);
    verifyNoInteractions(cacheKeyFactory, cacheService);
  }

  @Test
  void cacheFailureLogsDoNotLeakUrlsCachedJsonOrApiKeys(CapturedOutput output) {
    String sensitiveUrl = "https://example.com/jobs/123?token=raw-url-token-marker";
    ApplicationDraftRequest sensitiveRequest = new ApplicationDraftRequest(sensitiveUrl);
    when(cacheKeyFactory.create(sensitiveUrl)).thenReturn(CACHE_KEY_VALUE);
    when(cacheService.get(CACHE_KEY))
        .thenThrow(
            new IllegalStateException(
                "cached-json-marker openai-api-key-marker raw-url-token-marker"));
    when(aiServiceClient.parseJob(sensitiveRequest)).thenReturn(sampleDraft(sensitiveUrl));

    service.createDraft(sensitiveRequest);

    assertThat(output.getOut())
        .contains(
            "ai_draft_cache_read_failed url_host=example.com cache_key_suffix=abc error_type=IllegalStateException")
        .doesNotContain(
            sensitiveUrl, "raw-url-token-marker", "cached-json-marker", "openai-api-key-marker");
  }

  @Test
  void cacheLogsDoNotExposeIpLiteralHosts(CapturedOutput output) {
    AiDraftCacheKey ipKey =
        new AiDraftCacheKey(CACHE_KEY, "https://192.0.2.10/jobs/123", "192.0.2.10", "abc");
    when(cacheKeyFactory.create(REQUEST_URL)).thenReturn(ipKey);
    when(cacheService.get(CACHE_KEY)).thenReturn(Optional.of(sampleDraft(NORMALIZED_URL)));

    service.createDraft(request);

    assertThat(output.getOut()).contains("url_host=ip-literal").doesNotContain("192.0.2.10");
  }

  private static ApplicationDraftResponse sampleDraft(String jobUrl) {
    return new ApplicationDraftResponse(
        "Acme",
        "Backend Engineer",
        jobUrl,
        "Remote",
        "remote",
        ApplicationStage.INITIAL,
        "Role summary",
        List.of(),
        List.of());
  }
}
