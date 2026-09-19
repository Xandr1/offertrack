package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationDraftServiceTest {
  @Mock private AiServiceClient aiServiceClient;

  @Test
  void callsAiForEveryRequestAndReturnsItsResponseUnchanged() {
    ApplicationDraftRequest request = new ApplicationDraftRequest("https://example.com/jobs/123");
    ApplicationDraftResponse response =
        new ApplicationDraftResponse(
            "Acme",
            "Engineer",
            request.jobUrl(),
            "Remote",
            "remote",
            ApplicationStage.INITIAL,
            "Role summary",
            List.of(),
            List.of("Check the details"));
    when(aiServiceClient.parseJob(request)).thenReturn(response);
    ApplicationDraftService service = new ApplicationDraftService(aiServiceClient);

    assertThat(service.createDraft(request)).isSameAs(response);
    assertThat(service.createDraft(request)).isSameAs(response);
    verify(aiServiceClient, times(2)).parseJob(request);
  }

  @Test
  void preservesAiFailureAndCallsAiAgainOnRetry() {
    ApplicationDraftRequest request = new ApplicationDraftRequest("https://example.com/jobs/123");
    AiServiceUnavailableException failure = new AiServiceUnavailableException();
    when(aiServiceClient.parseJob(request)).thenThrow(failure);
    ApplicationDraftService service = new ApplicationDraftService(aiServiceClient);

    assertThatThrownBy(() -> service.createDraft(request)).isSameAs(failure);
    assertThatThrownBy(() -> service.createDraft(request)).isSameAs(failure);
    verify(aiServiceClient, times(2)).parseJob(request);
  }
}
