create table application_interviews (
  id uuid primary key,
  user_id uuid not null references users(id) on delete cascade,
  application_id uuid not null references job_applications(id) on delete cascade,
  title text not null,
  type text not null,
  status text not null,
  source text not null,
  scheduled_at timestamptz,
  meeting_url text,
  notes text,
  calendar_provider text,
  calendar_id text,
  calendar_event_id text,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint application_interviews_type_check check (
    type in (
      'hr',
      'recruiter',
      'technical',
      'coding',
      'system_design',
      'behavioral',
      'hiring_manager',
      'team_match',
      'final',
      'other'
    )
  ),
  constraint application_interviews_status_check check (
    status in (
      'planned',
      'scheduled',
      'completed',
      'cancelled'
    )
  ),
  constraint application_interviews_source_check check (
    source in (
      'manual',
      'google_calendar',
      'ai'
    )
  )
);
create index application_interviews_user_id_idx on application_interviews(user_id);
create index application_interviews_application_id_idx on application_interviews(application_id);
create index application_interviews_app_user_scheduled_created_idx
    on application_interviews(application_id, user_id, scheduled_at, created_at);
