package com.offertrack.auth.dto;

import com.offertrack.auth.validation.PasswordByteLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @Email(message = "Email must be valid") @NotBlank(message = "Email is required") String email,
    @NotBlank(message = "Password is required")
        @PasswordByteLength
        @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message =
                "Password must contain at least 1 lowercase letter, 1 uppercase letter and 1 digit")
        String password,
    @Size(max = 100, message = "Name must be at most 100 characters") String name) {
  @Override
  public String toString() {
    return "RegisterRequest[redacted]";
  }
}
