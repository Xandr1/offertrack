package com.offertrack.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public final class PasswordByteLengthValidator
    implements ConstraintValidator<PasswordByteLength, String> {
  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    // Presence is checked by @NotBlank; bcrypt's limit applies to encoded bytes.
    return value == null || value.getBytes(StandardCharsets.UTF_8).length <= 72;
  }
}
