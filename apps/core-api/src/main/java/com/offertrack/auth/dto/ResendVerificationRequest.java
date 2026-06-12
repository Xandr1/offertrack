package com.offertrack.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
    @Email(message = "Email must be valid") @NotBlank(message = "Email is required")
        String email) {}
