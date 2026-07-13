package com.offertrack.applications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import com.offertrack.config.RequestIdFilter;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpAiServiceClient implements AiServiceClient {
  private static final Logger log = LoggerFactory.getLogger(HttpAiServiceClient.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
  private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String internalApiKey;

  @Autowired
  public HttpAiServiceClient(AiServiceProperties properties, ObjectMapper objectMapper) {
    this(createRestClient(properties.getBaseUrl()), objectMapper, properties.getInternalApiKey());
  }

  HttpAiServiceClient(RestClient restClient, ObjectMapper objectMapper, String internalApiKey) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.internalApiKey = internalApiKey == null ? "" : internalApiKey.trim();
  }

  @Override
  public ApplicationDraftResponse parseJob(ApplicationDraftRequest request) {
    String requestId = RequestIdFilter.currentRequestId();

    if (internalApiKey.isBlank()) {
      log.warn(
          "ai_service_draft_failed request_id={} source_type=url error_code=AI_SERVICE_UNAVAILABLE",
          requestId);
      throw new AiServiceUnavailableException();
    }

    try {
      ApplicationDraftResponse response =
          restClient
              .post()
              .uri("/parse-job")
              .contentType(MediaType.APPLICATION_JSON)
              .header(INTERNAL_API_KEY_HEADER, internalApiKey)
              .header(REQUEST_ID_HEADER, requestId)
              .body(request)
              .retrieve()
              .body(ApplicationDraftResponse.class);

      if (response == null) {
        throw new AiServiceExtractionException();
      }

      log.info("ai_service_draft_succeeded request_id={} source_type=url", requestId);
      return response;
    } catch (RestClientResponseException exception) {
      RuntimeException mappedException = mapResponseException(exception);
      log.warn(
          "ai_service_draft_failed request_id={} source_type=url error_code={}",
          requestId,
          errorCode(mappedException));
      throw mappedException;
    } catch (ResourceAccessException exception) {
      if (containsTimeout(exception)) {
        log.warn(
            "ai_service_draft_failed request_id={} source_type=url error_code=AI_SERVICE_TIMEOUT",
            requestId);
        throw new AiServiceTimeoutException();
      }

      log.warn(
          "ai_service_draft_failed request_id={} source_type=url error_code=AI_SERVICE_UNAVAILABLE",
          requestId);
      throw new AiServiceUnavailableException();
    } catch (HttpMessageConversionException exception) {
      log.warn(
          "ai_service_draft_failed request_id={} source_type=url error_code=AI_SERVICE_EXTRACTION_FAILED",
          requestId);
      throw new AiServiceExtractionException();
    } catch (RestClientException exception) {
      if (containsTimeout(exception)) {
        log.warn(
            "ai_service_draft_failed request_id={} source_type=url error_code=AI_SERVICE_TIMEOUT",
            requestId);
        throw new AiServiceTimeoutException();
      }

      log.warn(
          "ai_service_draft_failed request_id={} source_type=url error_code=AI_SERVICE_EXTRACTION_FAILED",
          requestId);
      throw new AiServiceExtractionException();
    }
  }

  private static RestClient createRestClient(String baseUrl) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(READ_TIMEOUT);

    return RestClient.builder().baseUrl(baseUrl.trim()).requestFactory(requestFactory).build();
  }

  private RuntimeException mapResponseException(RestClientResponseException exception) {
    HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
    AiServiceErrorResponse serviceErrorResponse = parseErrorResponse(exception);

    if (status == HttpStatus.BAD_REQUEST || status == HttpStatus.UNPROCESSABLE_ENTITY) {
      return new AiServiceInvalidUrlException();
    }

    if (status == HttpStatus.GATEWAY_TIMEOUT) {
      return new AiServiceTimeoutException();
    }

    if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
      return new AiServiceUnavailableException();
    }

    if (serviceErrorResponse != null) {
      return switch (serviceErrorResponse.code()) {
        case "INVALID_JOB_URL" -> new AiServiceInvalidUrlException();
        case "JOB_FETCH_TIMEOUT" -> new AiServiceTimeoutException();
        case "JOB_FETCH_FAILED" -> new AiServiceFetchFailedException();
        case "AI_SERVICE_INTERNAL_ERROR" -> new AiServiceUnavailableException();
        case "JOB_PAGE_NOT_READABLE", "AI_EXTRACTION_FAILED" -> new AiServiceExtractionException();
        default -> new AiServiceExtractionException();
      };
    }

    if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
      return new AiServiceUnavailableException();
    }

    return new AiServiceExtractionException();
  }

  private AiServiceErrorResponse parseErrorResponse(RestClientResponseException exception) {
    byte[] responseBody = exception.getResponseBodyAsByteArray();
    if (responseBody.length == 0) {
      return null;
    }

    try {
      return objectMapper.readValue(responseBody, AiServiceErrorResponse.class);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static boolean containsTimeout(Throwable throwable) {
    Throwable current = throwable;

    while (current != null) {
      if (current instanceof SocketTimeoutException) {
        return true;
      }

      current = current.getCause();
    }

    return false;
  }

  private static String errorCode(RuntimeException exception) {
    if (exception instanceof AiServiceInvalidUrlException) {
      return "AI_SERVICE_INVALID_URL";
    }
    if (exception instanceof AiServiceTimeoutException) {
      return "AI_SERVICE_TIMEOUT";
    }
    if (exception instanceof AiServiceFetchFailedException) {
      return "AI_SERVICE_FETCH_FAILED";
    }
    if (exception instanceof AiServiceUnavailableException) {
      return "AI_SERVICE_UNAVAILABLE";
    }
    return "AI_SERVICE_EXTRACTION_FAILED";
  }

  private record AiServiceErrorResponse(String code, String message) {}
}
