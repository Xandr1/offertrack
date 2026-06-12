alter table users
    add column email_verified_at timestamptz null;

create table user_auth_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    purpose varchar(40) not null,
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    consumed_at timestamptz null,
    created_at timestamptz not null default now(),
    constraint user_auth_tokens_purpose_check
        check (purpose in ('email_verification', 'password_reset'))
);

create index user_auth_tokens_user_id_purpose_idx
    on user_auth_tokens (user_id, purpose);

create index user_auth_tokens_active_lookup_idx
    on user_auth_tokens (purpose, token_hash)
    where consumed_at is null;
