package com.offertrack.dashboard;

import static com.offertrack.jooq.generated.tables.JobApplications.JOB_APPLICATIONS;
import static org.jooq.impl.DSL.count;

import com.offertrack.applications.ApplicationStage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {
  private static final List<String> ACTIVE_STAGE_VALUES =
      List.of(
          ApplicationStage.INITIAL.value(),
          ApplicationStage.APPLIED.value(),
          ApplicationStage.INTERVIEWING.value());

  private final DSLContext dsl;

  public DashboardRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  public DashboardSummaryCounts getSummaryCounts(UUID userId, OffsetDateTime staleBefore) {
    return dsl.select(
            count()
                .filterWhere(JOB_APPLICATIONS.STAGE.in(ACTIVE_STAGE_VALUES))
                .as("active_processes"),
            count()
                .filterWhere(
                    JOB_APPLICATIONS
                        .STAGE
                        .in(ACTIVE_STAGE_VALUES)
                        .and(JOB_APPLICATIONS.UPDATED_AT.lt(staleBefore)))
                .as("needs_attention"),
            count()
                .filterWhere(JOB_APPLICATIONS.STAGE.eq(ApplicationStage.INTERVIEWING.value()))
                .as("interviewing"),
            count()
                .filterWhere(JOB_APPLICATIONS.STAGE.eq(ApplicationStage.OFFER.value()))
                .as("offers"),
            count()
                .filterWhere(JOB_APPLICATIONS.STAGE.eq(ApplicationStage.REJECTED.value()))
                .as("rejected"))
        .from(JOB_APPLICATIONS)
        .where(JOB_APPLICATIONS.USER_ID.eq(userId))
        .fetchSingle(
            record ->
                new DashboardSummaryCounts(
                    record.value1(),
                    record.value2(),
                    record.value3(),
                    record.value4(),
                    record.value5()));
  }

  public List<DashboardRecentApplication> listRecentApplications(UUID userId, int limit) {
    return dsl.select(
            JOB_APPLICATIONS.ID,
            JOB_APPLICATIONS.COMPANY_NAME,
            JOB_APPLICATIONS.POSITION_TITLE,
            JOB_APPLICATIONS.STAGE,
            JOB_APPLICATIONS.UPDATED_AT)
        .from(JOB_APPLICATIONS)
        .where(JOB_APPLICATIONS.USER_ID.eq(userId))
        .orderBy(JOB_APPLICATIONS.UPDATED_AT.desc())
        .limit(limit)
        .fetch(
            record ->
                new DashboardRecentApplication(
                    record.get(JOB_APPLICATIONS.ID),
                    record.get(JOB_APPLICATIONS.COMPANY_NAME),
                    record.get(JOB_APPLICATIONS.POSITION_TITLE),
                    ApplicationStage.fromValue(record.get(JOB_APPLICATIONS.STAGE)),
                    record.get(JOB_APPLICATIONS.UPDATED_AT)));
  }
}
