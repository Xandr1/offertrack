package com.offertrack;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(
        DockerImageName.parse(
                "postgres:16.14-bookworm@sha256:c95fd5346040eba2de3c435e14874af18f5d681fb5848d4f081dbead0878af28")
            .asCompatibleSubstituteFor("postgres"));
  }
}
