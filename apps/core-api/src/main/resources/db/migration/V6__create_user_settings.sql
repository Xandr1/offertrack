create table user_settings (
    user_id uuid primary key references users(id) on delete cascade,
    follow_up_after_applying_days integer not null default 7,
    upcoming_interview_days integer not null default 7,
    follow_up_after_interview_days integer not null default 2,
    target_role varchar(160),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
