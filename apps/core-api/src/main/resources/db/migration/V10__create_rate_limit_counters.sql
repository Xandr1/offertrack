create table rate_limit_counters (
    policy text not null,
    subject_type text not null,
    subject_hash text not null check (subject_hash ~ '^[0-9a-f]{64}$'),
    bucket bigint not null,
    request_count bigint not null check (request_count > 0),
    expires_at timestamptz not null,
    primary key (policy, subject_type, subject_hash, bucket)
);

create index rate_limit_counters_expires_at_idx on rate_limit_counters (expires_at);
