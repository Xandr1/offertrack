insert into users (
  id,
  email,
  password_hash,
  name,
  created_at,
  updated_at,
  email_verified_at
) values (
  '00000000-0000-0000-0000-000000000002',
  'application-user@e2e.invalid',
  '$2a$10$0d3vyAe1FWFUcTVfYV/qlOKSFddLFo4fxOR/sP9J7WIcR8tgAT8Y.',
  'Application Test User',
  now(),
  now(),
  now()
)
on conflict (id) do update set
  email = excluded.email,
  password_hash = excluded.password_hash,
  name = excluded.name,
  updated_at = excluded.updated_at,
  email_verified_at = excluded.email_verified_at;
