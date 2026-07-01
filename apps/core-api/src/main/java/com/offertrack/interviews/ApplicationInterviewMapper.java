package com.offertrack.interviews;

import com.offertrack.jooq.generated.tables.records.ApplicationInterviewsRecord;

public class ApplicationInterviewMapper {
  private ApplicationInterviewMapper() {}

  public static ApplicationInterview fromRecord(ApplicationInterviewsRecord record) {
    return new ApplicationInterview(
        record.getId(),
        record.getUserId(),
        record.getApplicationId(),
        InterviewType.fromValue(record.getType()),
        InterviewStatus.fromValue(record.getStatus()),
        record.getScheduledAt(),
        record.getFollowedUpAt(),
        record.getCreatedAt(),
        record.getUpdatedAt());
  }
}
