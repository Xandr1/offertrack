alter table application_interviews
  drop constraint if exists application_interviews_status_check;

update application_interviews
set status = case
  when status = 'planned' then 'initial'
  when status = 'completed' then 'scheduled'
  else status
end
where status in ('planned', 'completed');

alter table application_interviews
  alter column status set default 'initial',
  add constraint application_interviews_status_check check (
    status in (
      'initial',
      'scheduled',
      'passed',
      'rejected'
    )
  ),
  add column followed_up_at timestamptz;

alter table job_applications
  add column followed_up_at timestamptz;
