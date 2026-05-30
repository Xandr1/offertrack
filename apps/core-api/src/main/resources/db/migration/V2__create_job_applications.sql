create table job_applications (
  id uuid primary key,
  user_id uuid not null references users(id) on delete cascade,
  company_name text not null,
  position_title text not null,
  job_url text,
  source text,
  location text,
  work_mode text,
  salary_min numeric,
  salary_max numeric,
  salary_currency text,
  stage text not null,
  notes text,
  applied_at timestamptz,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  constraint job_applications_stage_check check (
    stage in (
      'initial',
      'applied',
      'interviewing',
      'rejected',
      'offer'
    )
  )
);
create index job_applications_user_id_idx on job_applications(user_id);
create index job_applications_user_stage_idx on job_applications(user_id, stage);
create index job_applications_user_updated_at_idx on job_applications(user_id, updated_at desc);