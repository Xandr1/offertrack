package com.offertrack.applications.dto;

import com.offertrack.interviews.dto.ApplicationInterviewResponse;
import java.util.List;

public record ApplicationWithInterviewsResponse(
    ApplicationResponse application, List<ApplicationInterviewResponse> interviews) {}
