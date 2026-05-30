package com.offertrack.applications;

import com.offertrack.jooq.generated.tables.records.JobApplicationsRecord;

public class ApplicationMapper {
  private ApplicationMapper() {}

  public static Application fromRecord(JobApplicationsRecord record) {
    return new Application(
        record.getId(),
        record.getUserId(),
        record.getCompanyName(),
        record.getPositionTitle(),
        record.getLocation(),
        record.getWorkMode(),
        ApplicationStage.fromValue(record.getStage()),
        record.getNotes(),
        record.getAppliedAt(),
        record.getCreatedAt(),
        record.getUpdatedAt());
  }
}
