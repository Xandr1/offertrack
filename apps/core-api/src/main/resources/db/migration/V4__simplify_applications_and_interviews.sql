update job_applications
set work_mode = lower(trim(work_mode))
where work_mode is not null;

update job_applications
set work_mode = null
where work_mode is not null
  and work_mode not in ('remote', 'hybrid', 'onsite');

alter table job_applications
  drop column if exists job_url,
  drop column if exists source,
  drop column if exists salary_min,
  drop column if exists salary_max,
  drop column if exists salary_currency;

alter table job_applications
  drop constraint if exists job_applications_work_mode_check;

alter table job_applications
  add constraint job_applications_work_mode_check check (
    work_mode is null
    or work_mode in (
      'remote',
      'hybrid',
      'onsite'
    )
  );

delete from application_interviews
where status = 'cancelled';

update application_interviews
set type = 'other'
where type not in (
  'recruiter',
  'hr',
  'technical',
  'hiring_manager',
  'team_match',
  'home_assignment',
  'behavioral',
  'other'
);

alter table application_interviews
  drop constraint if exists application_interviews_type_check,
  drop constraint if exists application_interviews_status_check,
  drop constraint if exists application_interviews_source_check;

alter table application_interviews
  drop column if exists title,
  drop column if exists meeting_url,
  drop column if exists notes,
  drop column if exists source,
  drop column if exists calendar_provider,
  drop column if exists calendar_id,
  drop column if exists calendar_event_id;

alter table application_interviews
  add constraint application_interviews_type_check check (
    type in (
      'recruiter',
      'hr',
      'technical',
      'hiring_manager',
      'team_match',
      'home_assignment',
      'behavioral',
      'other'
    )
  );

alter table application_interviews
  add constraint application_interviews_status_check check (
    status in (
      'planned',
      'scheduled',
      'completed',
      'passed',
      'rejected'
    )
  );
