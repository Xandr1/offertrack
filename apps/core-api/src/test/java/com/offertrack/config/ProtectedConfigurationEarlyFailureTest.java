package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.offertrack.CoreApiApplication;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

class ProtectedConfigurationEarlyFailureTest {
  @Test
  void missingTlsFailsBeforeAnyConnectionAttempt() {
    TrackingDriver.connectionAttempted.set(false);
    SpringApplication application = new SpringApplication(CoreApiApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    String[] arguments =
        Arrays.stream(validArgumentsExceptJwtSecret())
            .map(value -> value.replace("?sslmode=require", ""))
            .map(
                value ->
                    value.equals("--app.jwt.secret=short")
                        ? "--app.jwt.secret=jwt-signing-secret-which-is-at-least-thirty-two-bytes"
                        : value)
            .toArray(String[]::new);
    assertThatThrownBy(() -> application.run(arguments))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("spring.datasource.url")
        .hasMessageNotContaining("jdbc:");
    assertThat(TrackingDriver.connectionAttempted).isFalse();
  }

  @Test
  void invalidProtectedConfigurationFailsBeforeDatasourceConnectionAttempt() {
    TrackingDriver.connectionAttempted.set(false);
    SpringApplication application = new SpringApplication(CoreApiApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);

    assertThatThrownBy(() -> application.run(validArgumentsExceptJwtSecret()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.jwt.secret");
    assertThat(TrackingDriver.connectionAttempted).isFalse();
  }

  @Test
  void invalidRateLimitKeyFailsBeforeDatasourceConnectionAttempt() {
    TrackingDriver.connectionAttempted.set(false);
    SpringApplication application = new SpringApplication(CoreApiApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    String[] arguments = validArgumentsExceptJwtSecret();
    arguments =
        Arrays.stream(arguments)
            .map(
                value ->
                    value.startsWith("--app.jwt.secret=")
                        ? "--app.jwt.secret=jwt-signing-secret-which-is-at-least-thirty-two-bytes"
                        : value)
            .map(
                value ->
                    value.startsWith("--app.rate-limit.key-secret=")
                        ? "--app.rate-limit.key-secret=short-secret-marker"
                        : value)
            .toArray(String[]::new);

    String[] invalidArguments = arguments;
    assertThatThrownBy(() -> application.run(invalidArguments))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.rate-limit.key-secret")
        .hasMessageNotContaining("short-secret-marker");
    assertThat(TrackingDriver.connectionAttempted).isFalse();
  }

  private static String[] validArgumentsExceptJwtSecret() {
    return new String[] {
      "--spring.profiles.active=staging",
      "--spring.main.banner-mode=off",
      "--logging.level.root=OFF",
      "--spring.datasource.url=jdbc:postgresql://db.example.com:5432/offertrack?sslmode=require",
      "--spring.datasource.username=production_user",
      "--spring.datasource.password=production-database-password",
      "--spring.datasource.driver-class-name=" + TrackingDriver.class.getName(),
      "--spring.mail.host=smtp.example.com",
      "--spring.mail.port=587",
      "--spring.mail.username=smtp-user",
      "--spring.mail.password=production-smtp-password",
      "--spring.mail.properties.mail.smtp.starttls.enable=true",
      "--spring.mail.properties.mail.smtp.starttls.required=true",
      "--spring.mail.properties.mail.smtp.connectiontimeout=5s",
      "--spring.mail.properties.mail.smtp.timeout=10s",
      "--spring.mail.properties.mail.smtp.writetimeout=10s",
      "--spring.security.oauth2.client.registration.google.client-id=google-client-id",
      "--spring.security.oauth2.client.registration.google.client-secret=google-client-secret",
      "--app.mail.from=no-reply@example.com",
      "--app.jwt.secret=short",
      "--app.jwt.access-token-ttl=15m",
      "--app.oauth.authorization-request-cookie-signing-secret=oauth-cookie-signing-secret-which-is-long-enough",
      "--app.rate-limit.key-secret=rate-limit-hmac-secret-which-is-long-enough",
      "--app.management.dependency-health-key=dependency-health-key-which-is-long-enough-and-distinct",
      "--app.web.url=https://app.example.com",
      "--app.cors.allowed-origins=https://app.example.com",
      "--app.auth.cookie.name=access_token",
      "--app.auth.cookie.path=/",
      "--app.auth.cookie.secure=true",
      "--app.auth.cookie.same-site=Lax",
      "--app.ai-service.base-url=https://ai-service.example.com",
      "--app.ai-service.internal-api-key=ai-service-internal-key-which-is-long-enough",
      "--app.ai-service.auth-mode=google-id-token",
      "--app.ai-service.audience=https://ai-service.example.com",
      "--server.forward-headers-strategy=none"
    };
  }

  public static final class TrackingDriver implements Driver {
    private static final AtomicBoolean connectionAttempted = new AtomicBoolean();

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
      connectionAttempted.set(true);
      throw new SQLException("Datasource connection must not be attempted");
    }

    @Override
    public boolean acceptsURL(String url) {
      return url != null && url.startsWith("jdbc:postgresql:");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
      return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
      return 1;
    }

    @Override
    public int getMinorVersion() {
      return 0;
    }

    @Override
    public boolean jdbcCompliant() {
      return false;
    }

    @Override
    public Logger getParentLogger() {
      return Logger.getGlobal();
    }
  }
}
