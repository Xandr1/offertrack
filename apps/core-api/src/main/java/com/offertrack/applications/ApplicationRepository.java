package com.offertrack.applications;

import static com.offertrack.jooq.generated.tables.JobApplications.JOB_APPLICATIONS;

import com.offertrack.applications.dto.CreateApplicationRequest;
import com.offertrack.applications.dto.ReplaceApplicationRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ApplicationRepository {
  private final DSLContext dsl;

  public ApplicationRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public Application create(UUID userId, CreateApplicationRequest request, ApplicationStage stage) {
    UUID id = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();

    return dsl.insertInto(JOB_APPLICATIONS)
        .set(JOB_APPLICATIONS.ID, id)
        .set(JOB_APPLICATIONS.USER_ID, userId)
        .set(JOB_APPLICATIONS.COMPANY_NAME, request.companyName())
        .set(JOB_APPLICATIONS.POSITION_TITLE, request.positionTitle())
        .set(JOB_APPLICATIONS.LOCATION, request.location())
        .set(JOB_APPLICATIONS.WORK_MODE, request.workMode())
        .set(JOB_APPLICATIONS.STAGE, stage.value())
        .set(JOB_APPLICATIONS.NOTES, request.notes())
        .set(JOB_APPLICATIONS.APPLIED_AT, request.appliedAt())
        .set(JOB_APPLICATIONS.CREATED_AT, now)
        .set(JOB_APPLICATIONS.UPDATED_AT, now)
        .returning()
        .fetchOne(ApplicationMapper::fromRecord);
  }

  public List<Application> listByUser(UUID userId) {
    return dsl.selectFrom(JOB_APPLICATIONS)
        .where(JOB_APPLICATIONS.USER_ID.eq(userId))
        .orderBy(JOB_APPLICATIONS.UPDATED_AT.desc())
        .fetch(ApplicationMapper::fromRecord);
  }

  public Optional<Application> findByIdForUser(UUID id, UUID userId) {
    return dsl.selectFrom(JOB_APPLICATIONS)
        .where(JOB_APPLICATIONS.ID.eq(id))
        .and(JOB_APPLICATIONS.USER_ID.eq(userId))
        .fetchOptional(ApplicationMapper::fromRecord);
  }

  public boolean delete(UUID id, UUID userId) {
    int deletedRows =
        dsl.deleteFrom(JOB_APPLICATIONS)
            .where(JOB_APPLICATIONS.ID.eq(id))
            .and(JOB_APPLICATIONS.USER_ID.eq(userId))
            .execute();

    return deletedRows > 0;
  }

  public Optional<Application> replace(
      UUID id, UUID userId, ReplaceApplicationRequest request, ApplicationStage stage) {
    OffsetDateTime now = OffsetDateTime.now();

    return dsl.update(JOB_APPLICATIONS)
        .set(JOB_APPLICATIONS.UPDATED_AT, now)
        .set(JOB_APPLICATIONS.COMPANY_NAME, request.companyName())
        .set(JOB_APPLICATIONS.POSITION_TITLE, request.positionTitle())
        .set(JOB_APPLICATIONS.LOCATION, request.location())
        .set(JOB_APPLICATIONS.WORK_MODE, request.workMode())
        .set(JOB_APPLICATIONS.STAGE, stage.value())
        .set(JOB_APPLICATIONS.NOTES, request.notes())
        .set(JOB_APPLICATIONS.APPLIED_AT, request.appliedAt())
        .where(JOB_APPLICATIONS.ID.eq(id))
        .and(JOB_APPLICATIONS.USER_ID.eq(userId))
        .returning()
        .fetchOptional(ApplicationMapper::fromRecord);
  }

  public Optional<Application> updateStage(UUID id, UUID userId, ApplicationStage stage) {
    OffsetDateTime now = OffsetDateTime.now();

    return dsl.update(JOB_APPLICATIONS)
        .set(JOB_APPLICATIONS.STAGE, stage.value())
        .set(JOB_APPLICATIONS.UPDATED_AT, now)
        .where(JOB_APPLICATIONS.ID.eq(id))
        .and(JOB_APPLICATIONS.USER_ID.eq(userId))
        .returning()
        .fetchOptional(ApplicationMapper::fromRecord);
  }
}
