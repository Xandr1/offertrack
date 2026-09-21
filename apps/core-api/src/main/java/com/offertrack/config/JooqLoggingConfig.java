package com.offertrack.config;

import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqLoggingConfig {
  @Bean
  DefaultConfigurationCustomizer credentialSafeSqlLogging() {
    // jOOQ's execution logger renders bind values and fetched user data at DEBUG.
    // Application diagnostics and PostgreSQL error logging remain enabled.
    return configuration -> configuration.settings().withExecuteLogging(false);
  }
}
