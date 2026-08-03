package com.offertrack.applications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertrack.applications.dto.ApplicationDraftRequest;
import com.offertrack.config.RequestIdFilter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class HttpAiServiceClientTest {
  private static final String INTERNAL_API_KEY = "test-internal-key";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private HttpServer server;
  private ExecutorService serverExecutor;

  @AfterEach
  void stopServer() {
    MDC.clear();
    if (server != null) {
      server.stop(0);
    }
    if (serverExecutor != null) {
      serverExecutor.shutdownNow();
    }
  }

  @Test
  void mapsBadRequestToInvalidUrl() throws Exception {
    startServer(exchange -> sendJson(exchange, 400, serviceError("INVALID_JOB_URL")));

    assertThatThrownBy(() -> client().parseJob(request()))
        .isInstanceOf(AiServiceInvalidUrlException.class);
  }

  @Test
  void mapsUnprocessableEntityToInvalidUrl() throws Exception {
    startServer(exchange -> sendJson(exchange, 422, serviceError("INVALID_JOB_URL")));

    assertThatThrownBy(() -> client().parseJob(request()))
        .isInstanceOf(AiServiceInvalidUrlException.class);
  }

  @Test
  void mapsStructuredFetchFailureToFetchException() throws Exception {
    startServer(exchange -> sendJson(exchange, 502, serviceError("JOB_FETCH_FAILED")));

    assertThatThrownBy(() -> client().parseJob(request()))
        .isInstanceOf(AiServiceFetchFailedException.class);
  }

  @Test
  void mapsStructuredExtractionFailureToExtractionException() throws Exception {
    startServer(exchange -> sendJson(exchange, 502, serviceError("AI_EXTRACTION_FAILED")));

    assertThatThrownBy(() -> client().parseJob(request()))
        .isInstanceOf(AiServiceExtractionException.class);
  }

  @Test
  void mapsStructuredInternalErrorToUnavailableException() throws Exception {
    startServer(exchange -> sendJson(exchange, 500, serviceError("AI_SERVICE_INTERNAL_ERROR")));

    assertThatThrownBy(() -> client().parseJob(request()))
        .isInstanceOf(AiServiceUnavailableException.class);
  }

  @Test
  void mapsPlainInternalServerErrorToUnavailableException() throws Exception {
    startServer(exchange -> send(exchange, 500, "Internal Server Error"));

    assertThatThrownBy(() -> client().parseJob(request()))
        .isInstanceOf(AiServiceUnavailableException.class);
  }

  @Test
  void mapsGatewayTimeoutToTimeoutException() throws Exception {
    startServer(exchange -> sendJson(exchange, 504, serviceError("JOB_FETCH_TIMEOUT")));

    assertThatThrownBy(() -> client().parseJob(request()))
        .isInstanceOf(AiServiceTimeoutException.class);
  }

  @Test
  void mapsReadTimeoutToTimeoutException() throws Exception {
    startServer(
        exchange -> {
          sleep(Duration.ofMillis(500));
          sendJson(exchange, 200, successBody());
        });

    assertThatThrownBy(() -> client(Duration.ofMillis(50)).parseJob(request()))
        .isInstanceOf(AiServiceTimeoutException.class);
  }

  @Test
  void mapsConnectionRefusedToUnavailableException() throws Exception {
    int unusedPort = unusedPort();
    HttpAiServiceClient client =
        new HttpAiServiceClient(
            restClient("http://127.0.0.1:" + unusedPort, Duration.ofMillis(100)),
            objectMapper,
            INTERNAL_API_KEY);

    assertThatThrownBy(() -> client.parseJob(request()))
        .isInstanceOf(AiServiceUnavailableException.class);
  }

  @Test
  void mapsMissingInternalApiKeyToUnavailableBeforeCallingService() throws Exception {
    AtomicReference<Boolean> called = new AtomicReference<>(false);
    startServer(
        exchange -> {
          called.set(true);
          sendJson(exchange, 200, successBody());
        });
    HttpAiServiceClient client =
        new HttpAiServiceClient(
            restClient("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2)),
            objectMapper,
            "");

    assertThatThrownBy(() -> client.parseJob(request()))
        .isInstanceOf(AiServiceUnavailableException.class);
    assertThat(called).hasValue(false);
  }

  @Test
  void mapsSuccessfulNullBodyToExtractionException() throws Exception {
    startServer(exchange -> send(exchange, 200, ""));

    assertThatThrownBy(() -> client().parseJob(request()))
        .isInstanceOf(AiServiceExtractionException.class);
  }

  @Test
  void mapsMalformedJsonToExtractionException() throws Exception {
    startServer(exchange -> send(exchange, 200, "not-json"));

    assertThatThrownBy(() -> client().parseJob(request()))
        .isInstanceOf(AiServiceExtractionException.class);
  }

  @Test
  void mapsNonDeserializableBodyToExtractionException() throws Exception {
    startServer(
        exchange ->
            sendJson(
                exchange,
                200,
                """
                {
                  "companyName": "Acme",
                  "positionTitle": "Backend Engineer",
                  "jobUrl": "https://example.com/jobs/123",
                  "stage": { "invalid": true },
                  "interviews": [],
                  "warnings": []
                }
                """));

    assertThatThrownBy(() -> client().parseJob(request()))
        .isInstanceOf(AiServiceExtractionException.class);
  }

  @Test
  void sendsInternalApiKeyAndRequestIdHeaders() throws Exception {
    AtomicReference<String> internalApiKeyHeader = new AtomicReference<>();
    AtomicReference<String> requestIdHeader = new AtomicReference<>();
    AtomicReference<String> serverlessAuthorizationHeader = new AtomicReference<>();
    AtomicInteger tokenProviderCalls = new AtomicInteger();
    startServer(
        exchange -> {
          internalApiKeyHeader.set(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
          requestIdHeader.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
          serverlessAuthorizationHeader.set(
              exchange.getRequestHeaders().getFirst("X-Serverless-Authorization"));
          sendJson(exchange, 200, successBody());
        });

    MDC.put(RequestIdFilter.MDC_KEY, "incoming-correlation-id");
    HttpAiServiceClient client =
        client(
            AiServiceAuthMode.INTERNAL_KEY,
            "",
            audience -> {
              tokenProviderCalls.incrementAndGet();
              return "must-not-be-used";
            });
    var response = client.parseJob(request());

    assertThat(response.companyName()).isEqualTo("Acme");
    assertThat(internalApiKeyHeader).hasValue(INTERNAL_API_KEY);
    assertThat(requestIdHeader).hasValue("incoming-correlation-id");
    assertThat(serverlessAuthorizationHeader).hasValue(null);
    assertThat(tokenProviderCalls).hasValue(0);
  }

  @Test
  void googleModeRequestsExactAudienceAndSendsBothAuthenticationHeaders() throws Exception {
    String audience = "https://ai-service.example.com/";
    AtomicReference<String> requestedAudience = new AtomicReference<>();
    AtomicReference<String> internalApiKeyHeader = new AtomicReference<>();
    AtomicReference<String> serverlessAuthorizationHeader = new AtomicReference<>();
    AtomicReference<String> requestIdHeader = new AtomicReference<>();
    startServer(
        exchange -> {
          internalApiKeyHeader.set(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"));
          serverlessAuthorizationHeader.set(
              exchange.getRequestHeaders().getFirst("X-Serverless-Authorization"));
          requestIdHeader.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
          sendJson(exchange, 200, successBody());
        });

    HttpAiServiceClient client =
        client(
            AiServiceAuthMode.GOOGLE_ID_TOKEN,
            audience,
            requested -> {
              requestedAudience.set(requested);
              return "deterministic-google-token";
            });

    MDC.put(RequestIdFilter.MDC_KEY, "google-mode-request-id");
    assertThat(client.parseJob(request()).companyName()).isEqualTo("Acme");
    assertThat(requestedAudience).hasValue(audience);
    assertThat(internalApiKeyHeader).hasValue(INTERNAL_API_KEY);
    assertThat(serverlessAuthorizationHeader).hasValue("Bearer deterministic-google-token");
    assertThat(requestIdHeader).hasValue("google-mode-request-id");
  }

  @Test
  void tokenAcquisitionFailureSendsNoRequestAndDoesNotLeakProviderError(CapturedOutput output)
      throws Exception {
    AtomicInteger requests = new AtomicInteger();
    startServer(
        exchange -> {
          requests.incrementAndGet();
          sendJson(exchange, 200, successBody());
        });
    MDC.put(RequestIdFilter.MDC_KEY, "safe-request-id");
    HttpAiServiceClient client =
        client(
            AiServiceAuthMode.GOOGLE_ID_TOKEN,
            "https://ai-service.example.com",
            audience -> {
              throw new IllegalStateException("raw-provider-error-with-secret");
            });

    assertThatThrownBy(() -> client.parseJob(request()))
        .isInstanceOf(AiServiceUnavailableException.class);
    assertThat(requests).hasValue(0);
    assertThat(output)
        .contains("request_id=safe-request-id")
        .contains("error_category=IDENTITY_TOKEN_ACQUISITION_FAILED")
        .doesNotContain("raw-provider-error-with-secret")
        .doesNotContain("ai-service.example.com");
  }

  private HttpAiServiceClient client() {
    return client(Duration.ofSeconds(2));
  }

  private HttpAiServiceClient client(Duration readTimeout) {
    return new HttpAiServiceClient(
        restClient("http://127.0.0.1:" + server.getAddress().getPort(), readTimeout),
        objectMapper,
        INTERNAL_API_KEY);
  }

  private HttpAiServiceClient client(
      AiServiceAuthMode authMode,
      String audience,
      AiServiceIdentityTokenProvider identityTokenProvider) {
    return new HttpAiServiceClient(
        restClient("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2)),
        objectMapper,
        INTERNAL_API_KEY,
        authMode,
        audience,
        identityTokenProvider);
  }

  private static RestClient restClient(String baseUrl, Duration readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(200));
    requestFactory.setReadTimeout(readTimeout);

    return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  private void startServer(ExchangeHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/parse-job", handler::handle);
    serverExecutor = Executors.newSingleThreadExecutor();
    server.setExecutor(serverExecutor);
    server.start();
  }

  private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
    exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    send(exchange, status, body);
  }

  private static void send(HttpExchange exchange, int status, String body) throws IOException {
    byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, responseBytes.length);
    exchange.getResponseBody().write(responseBytes);
    exchange.close();
  }

  private static String serviceError(String code) {
    return """
        {
          "code": "%s",
          "message": "Service failed."
        }
        """
        .formatted(code);
  }

  private static String successBody() {
    return """
        {
          "companyName": "Acme",
          "positionTitle": "Backend Engineer",
          "jobUrl": "https://example.com/jobs/123",
          "location": "Remote",
          "workMode": "remote",
          "stage": "initial",
          "notes": "Acme is hiring a backend engineer. The role is remote.",
          "interviews": [],
          "warnings": []
        }
        """;
  }

  private static ApplicationDraftRequest request() {
    return new ApplicationDraftRequest("https://example.com/jobs/123");
  }

  private static int unusedPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  @FunctionalInterface
  private interface ExchangeHandler {
    void handle(HttpExchange exchange) throws IOException;
  }
}
