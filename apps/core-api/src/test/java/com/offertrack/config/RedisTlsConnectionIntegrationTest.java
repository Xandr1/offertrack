package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class RedisTlsConnectionIntegrationTest {
  private static final DockerImageName REDIS_IMAGE =
      DockerImageName.parse(
          "redis:7.4.9-alpine@sha256:b1addbe72465a718643cff9e60a58e6df1841e29d6d7d60c9a85d8d72f08d1a7");
  private static final TlsFiles TLS_FILES = generateTlsFiles();

  @Container
  private static final GenericContainer<?> PLAIN_REDIS =
      new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

  @Container
  private static final GenericContainer<?> TLS_REDIS =
      new GenericContainer<>(REDIS_IMAGE)
          .withExposedPorts(6379)
          .withCopyFileToContainer(
              MountableFile.forHostPath(TLS_FILES.serverCertificate()), "/tmp/server.crt")
          .withCopyFileToContainer(
              MountableFile.forHostPath(TLS_FILES.serverPrivateKey()), "/tmp/server.key")
          .withCopyFileToContainer(
              MountableFile.forHostPath(TLS_FILES.caCertificate()), "/tmp/ca.crt")
          .withCommand(
              "sh",
              "-c",
              """
              set -eu
              exec redis-server --port 0 --tls-port 6379 \
                --tls-cert-file /tmp/server.crt \
                --tls-key-file /tmp/server.key \
                --tls-ca-cert-file /tmp/ca.crt \
                --tls-auth-clients no
              """);

  private static String serverCaCertificate;

  @BeforeAll
  static void loadGeneratedServerCa() throws IOException {
    serverCaCertificate =
        Files.readString(TLS_FILES.caCertificate(), StandardCharsets.US_ASCII).trim();
  }

  @AfterAll
  static void removeGeneratedTlsFiles() throws IOException {
    try (var paths = Files.walk(TLS_FILES.directory())) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  @Test
  void tlsDisabledUsesTheExistingPlainRedisConnection() {
    redisContext(PLAIN_REDIS, false, null)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              LettuceConnectionFactory connectionFactory =
                  context.getBean(LettuceConnectionFactory.class);
              assertThat(connectionFactory.getClientConfiguration().isUseSsl()).isFalse();
              assertThat(context.getBean(StringRedisTemplate.class).getConnectionFactory())
                  .isSameAs(connectionFactory);
              assertThat(connectionFactory.getConnection().ping()).isEqualTo("PONG");
            });
  }

  @Test
  void connectsThroughTlsWithPeerVerificationAndTheFullCaSet() {
    String activeCaSet =
        serverCaCertificate + "\n" + ProtectedConfigurationRulesTest.redisOtherCaCertificate();

    redisContext(TLS_REDIS, true, activeCaSet)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              LettuceConnectionFactory connectionFactory =
                  context.getBean(LettuceConnectionFactory.class);
              assertThat(connectionFactory.getClientConfiguration().isUseSsl()).isTrue();
              assertThat(connectionFactory.getClientConfiguration().isVerifyPeer()).isTrue();
              assertThat(connectionFactory.getConnection().ping()).isEqualTo("PONG");
            });
  }

  @Test
  void rejectsATlsServerWhoseCertificateIsNotInTheConfiguredTrustSet() {
    redisContext(TLS_REDIS, true, ProtectedConfigurationRulesTest.redisCaCertificate())
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              LettuceConnectionFactory connectionFactory =
                  context.getBean(LettuceConnectionFactory.class);
              assertThat(connectionFactory.getClientConfiguration().isVerifyPeer()).isTrue();
              assertThatThrownBy(() -> connectionFactory.getConnection().ping())
                  .isInstanceOf(DataAccessResourceFailureException.class);
            });
  }

  private static ApplicationContextRunner redisContext(
      GenericContainer<?> redis, boolean tlsEnabled, String caCertificates) {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(SslAutoConfiguration.class, RedisAutoConfiguration.class))
            .withPropertyValues(
                "spring.data.redis.host=" + redis.getHost(),
                "spring.data.redis.port=" + redis.getMappedPort(6379),
                "spring.data.redis.connect-timeout=1s",
                "spring.data.redis.timeout=1s",
                "spring.data.redis.ssl.enabled=" + tlsEnabled);

    if (!tlsEnabled) {
      return runner;
    }

    return runner.withPropertyValues(
        "spring.data.redis.ssl.bundle=offertrack-test-redis",
        "spring.ssl.bundle.pem.offertrack-test-redis.truststore.certificate=" + caCertificates);
  }

  private static TlsFiles generateTlsFiles() {
    try {
      Path directory = Files.createTempDirectory("offertrack-redis-tls-");
      Path caCertificate = directory.resolve("ca.crt");
      Path caPrivateKey = directory.resolve("ca.key");
      Path serverCertificate = directory.resolve("server.crt");
      Path serverPrivateKey = directory.resolve("server.key");
      Path serverRequest = directory.resolve("server.csr");
      Path serverExtensions = directory.resolve("server.ext");

      runOpenSsl(
          directory,
          "req",
          "-x509",
          "-newkey",
          "rsa:2048",
          "-nodes",
          "-keyout",
          caPrivateKey.toString(),
          "-out",
          caCertificate.toString(),
          "-days",
          "2",
          "-subj",
          "/CN=OfferTrack Redis Integration CA",
          "-addext",
          "basicConstraints=critical,CA:TRUE",
          "-addext",
          "keyUsage=critical,keyCertSign,cRLSign");
      runOpenSsl(
          directory,
          "req",
          "-newkey",
          "rsa:2048",
          "-nodes",
          "-keyout",
          serverPrivateKey.toString(),
          "-out",
          serverRequest.toString(),
          "-subj",
          "/CN=localhost");
      Files.writeString(
          serverExtensions,
          "subjectAltName=DNS:localhost,IP:127.0.0.1\n"
              + "basicConstraints=critical,CA:FALSE\n"
              + "keyUsage=critical,digitalSignature,keyEncipherment\n"
              + "extendedKeyUsage=serverAuth\n",
          StandardCharsets.US_ASCII);
      runOpenSsl(
          directory,
          "x509",
          "-req",
          "-in",
          serverRequest.toString(),
          "-CA",
          caCertificate.toString(),
          "-CAkey",
          caPrivateKey.toString(),
          "-CAcreateserial",
          "-out",
          serverCertificate.toString(),
          "-days",
          "2",
          "-sha256",
          "-extfile",
          serverExtensions.toString());

      return new TlsFiles(directory, caCertificate, serverCertificate, serverPrivateKey);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ExceptionInInitializerError(exception);
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static void runOpenSsl(Path directory, String... arguments)
      throws IOException, InterruptedException {
    String[] command = new String[arguments.length + 1];
    command[0] = "openssl";
    System.arraycopy(arguments, 0, command, 1, arguments.length);
    Process process =
        new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    if (process.waitFor() != 0) {
      throw new IOException("OpenSSL could not generate Redis test certificates");
    }
  }

  private record TlsFiles(
      Path directory, Path caCertificate, Path serverCertificate, Path serverPrivateKey) {}
}
