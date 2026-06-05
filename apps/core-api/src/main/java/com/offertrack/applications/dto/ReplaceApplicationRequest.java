package com.offertrack.applications.dto;

import com.offertrack.applications.ApplicationStage;
import com.offertrack.applications.validation.HttpOrHttpsUrl;
import com.offertrack.applications.validation.JobUrlNormalizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public record ReplaceApplicationRequest(
    @NotBlank(message = "Company name is required")
        @Size(max = 200, message = "Company name must be at most 200 characters")
        String companyName,
    @NotBlank(message = "Position title is required")
        @Size(max = 200, message = "Position title must be at most 200 characters")
        String positionTitle,
    @Size(max = 2048, message = "Job URL must be at most 2048 characters") @HttpOrHttpsUrl
        String jobUrl,
    @Size(max = 200, message = "Location must be at most 200 characters") String location,
    @Pattern(
            regexp = "^(remote|hybrid|onsite)$",
            message = "Work mode must be one of: remote, hybrid, onsite")
        String workMode,
    ApplicationStage stage,
    String notes,
    OffsetDateTime appliedAt,
    @NotNull(message = "Interviews are required") @Valid
        List<ReplaceApplicationInterviewItemRequest> interviews) {
  public ReplaceApplicationRequest {
    jobUrl = JobUrlNormalizer.normalize(jobUrl);
  }
}
