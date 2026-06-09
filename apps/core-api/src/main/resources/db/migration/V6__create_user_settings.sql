create table user_settings (
    user_id uuid primary key references users(id) on delete cascade,
    follow_up_after_applying_days integer not null default 7
        constraint user_settings_follow_up_after_applying_days_check
        check (follow_up_after_applying_days between 1 and 60),
    upcoming_interview_days integer not null default 7
        constraint user_settings_upcoming_interview_days_check
        check (upcoming_interview_days between 1 and 60),
    follow_up_after_interview_days integer not null default 2
        constraint user_settings_follow_up_after_interview_days_check
        check (follow_up_after_interview_days between 1 and 30),
    target_role varchar(160),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
