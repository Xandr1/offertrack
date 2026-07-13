package com.offertrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsExplicitOrigins() {
    CorsProperties properties = new CorsProperties();
    properties.setAllowedOrigins(List.of("http://localhost:3000", "https://preview.example.com"));

    assertThat(validator.validate(properties)).isEmpty();
  }

  @Test
  void rejectsWildcardAndEmptyOriginsForCredentialedCors() {
    CorsProperties wildcard = new CorsProperties();
    wildcard.setAllowedOrigins(List.of("*"));
    CorsProperties empty = new CorsProperties();
    empty.setAllowedOrigins(List.of());

    assertThat(validator.validate(wildcard)).isNotEmpty();
    assertThat(validator.validate(empty)).isNotEmpty();
  }
}
