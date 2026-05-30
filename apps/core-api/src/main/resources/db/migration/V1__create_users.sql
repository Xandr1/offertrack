create table users (
    id uuid primary key,
    email text not null unique,
    password_hash text not null,
    name text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);