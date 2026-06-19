package com.offertrack.applications.dto;

import com.offertrack.applications.validation.HttpOrHttpsUrl;
import com.offertrack.applications.validation.JobUrlNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplicationDraftRequest(
    @NotBlank(message = "Job URL is required")
        @Size(max = 2048, message = "Job URL must be at most 2048 characters")
        @HttpOrHttpsUrl
        String jobUrl) {
  public ApplicationDraftRequest {
    jobUrl = JobUrlNormalizer.normalize(jobUrl);
  }
}
