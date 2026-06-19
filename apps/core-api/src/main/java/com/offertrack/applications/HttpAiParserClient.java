package com.offertrack.applications;

import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.applications.dto.ApplicationDraftResponse;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpAiParserClient implements AiParserClient {
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  private final RestClient restClient;

  @Autowired
  public HttpAiParserClient(@Value("${app.ai-parser.base-url}") String baseUrl) {
    this(createRestClient(baseUrl));
  }

  HttpAiParserClient(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public ApplicationDraftResponse parseJob(ApplicationDraftRequest request) {
    try {
      ApplicationDraftResponse response =
          restClient
              .post()
              .uri("/parse-job")
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(ApplicationDraftResponse.class);

      if (response == null) {
        throw new AiParserExtractionException();
      }

      return response;
    } catch (RestClientResponseException exception) {
      throw mapResponseException(exception);
    } catch (ResourceAccessException exception) {
      if (containsTimeout(exception)) {
        throw new AiParserTimeoutException();
      }

      throw new AiParserUnavailableException();
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

  private static RuntimeException mapResponseException(RestClientResponseException exception) {
    HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());

    if (status == HttpStatus.BAD_REQUEST || status == HttpStatus.UNPROCESSABLE_ENTITY) {
      return new AiParserInvalidUrlException();
    }

    if (status == HttpStatus.GATEWAY_TIMEOUT) {
      return new AiParserTimeoutException();
    }

    return new AiParserExtractionException();
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
}
