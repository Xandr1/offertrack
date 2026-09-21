package com.offertrack.auth.dto;

import com.offertrack.auth.validation.PasswordByteLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank(message = "Token is required") String token,
    @NotBlank(message = "Password is required")
        @PasswordByteLength
        @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message =
                "Password must contain at least 1 lowercase letter, 1 uppercase letter and 1 digit")
        String newPassword) {
  @Override
  public String toString() {
    return "ResetPasswordRequest[redacted]";
  }
}
