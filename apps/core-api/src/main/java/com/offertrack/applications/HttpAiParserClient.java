package com.offertrack.applications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class HttpAiParserClient implements AiParserClient {
  private static final Logger log = LoggerFactory.getLogger(HttpAiParserClient.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
  private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String internalApiKey;

  @Autowired
  public HttpAiParserClient(
      @Value("${app.ai-parser.base-url}") String baseUrl,
      @Value("${app.ai-parser.internal-api-key}") String internalApiKey,
      ObjectMapper objectMapper) {
    this(createRestClient(baseUrl), objectMapper, internalApiKey);
  }

  HttpAiParserClient(RestClient restClient, ObjectMapper objectMapper, String internalApiKey) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.internalApiKey = internalApiKey == null ? "" : internalApiKey.trim();
  }

  @Override
  public ApplicationDraftResponse parseJob(ApplicationDraftRequest request) {
    String requestId = UUID.randomUUID().toString();

    if (internalApiKey.isBlank()) {
      log.warn(
          "ai_parser_draft_failed request_id={} source_type=url error_code=AI_PARSER_UNAVAILABLE",
          requestId);
      throw new AiParserUnavailableException();
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
        throw new AiParserExtractionException();
      }

      log.info("ai_parser_draft_succeeded request_id={} source_type=url", requestId);
      return response;
    } catch (RestClientResponseException exception) {
      RuntimeException mappedException = mapResponseException(exception);
      log.warn(
          "ai_parser_draft_failed request_id={} source_type=url error_code={}",
          requestId,
          errorCode(mappedException));
      throw mappedException;
    } catch (ResourceAccessException exception) {
      if (containsTimeout(exception)) {
        log.warn(
            "ai_parser_draft_failed request_id={} source_type=url error_code=AI_PARSER_TIMEOUT",
            requestId);
        throw new AiParserTimeoutException();
      }

      log.warn(
          "ai_parser_draft_failed request_id={} source_type=url error_code=AI_PARSER_UNAVAILABLE",
          requestId);
      throw new AiParserUnavailableException();
    } catch (HttpMessageConversionException exception) {
      log.warn(
          "ai_parser_draft_failed request_id={} source_type=url error_code=AI_PARSER_EXTRACTION_FAILED",
          requestId);
      throw new AiParserExtractionException();
    } catch (RestClientException exception) {
      if (containsTimeout(exception)) {
        log.warn(
            "ai_parser_draft_failed request_id={} source_type=url error_code=AI_PARSER_TIMEOUT",
            requestId);
        throw new AiParserTimeoutException();
      }

      log.warn(
          "ai_parser_draft_failed request_id={} source_type=url error_code=AI_PARSER_EXTRACTION_FAILED",
          requestId);
      throw new AiParserExtractionException();
    }
  }

  private static RestClient createRestClient(String baseUrl) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(READ_TIMEOUT);

    return RestClient.builder()
        .baseUrl(baseUrl == null || baseUrl.isBlank() ? "http://localhost:8000" : baseUrl.trim())
        .requestFactory(requestFactory)
        .build();
  }

  private RuntimeException mapResponseException(RestClientResponseException exception) {
    HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
    AiParserErrorResponse parserErrorResponse = parseErrorResponse(exception);

    if (status == HttpStatus.BAD_REQUEST || status == HttpStatus.UNPROCESSABLE_ENTITY) {
      return new AiParserInvalidUrlException();
    }

    if (status == HttpStatus.GATEWAY_TIMEOUT) {
      return new AiParserTimeoutException();
    }

    if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
      return new AiParserUnavailableException();
    }

    if (parserErrorResponse != null) {
      return switch (parserErrorResponse.code()) {
        case "INVALID_JOB_URL" -> new AiParserInvalidUrlException();
        case "JOB_FETCH_TIMEOUT" -> new AiParserTimeoutException();
        case "JOB_FETCH_FAILED" -> new AiParserFetchFailedException();
        case "JOB_PAGE_NOT_READABLE", "AI_EXTRACTION_FAILED", "AI_PARSER_INTERNAL_ERROR" ->
            new AiParserExtractionException();
        default -> new AiParserExtractionException();
      };
    }

    return new AiParserExtractionException();
  }

  private AiParserErrorResponse parseErrorResponse(RestClientResponseException exception) {
    byte[] responseBody = exception.getResponseBodyAsByteArray();
    if (responseBody.length == 0) {
      return null;
    }

    try {
      return objectMapper.readValue(responseBody, AiParserErrorResponse.class);
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
    if (exception instanceof AiParserInvalidUrlException) {
      return "AI_PARSER_INVALID_URL";
    }
    if (exception instanceof AiParserTimeoutException) {
      return "AI_PARSER_TIMEOUT";
    }
    if (exception instanceof AiParserFetchFailedException) {
      return "AI_PARSER_FETCH_FAILED";
    }
    if (exception instanceof AiParserUnavailableException) {
      return "AI_PARSER_UNAVAILABLE";
    }
    return "AI_PARSER_EXTRACTION_FAILED";
  }

  private record AiParserErrorResponse(String code, String message) {}
}
