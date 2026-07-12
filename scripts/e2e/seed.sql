insert into users (
  id,
  email,
  password_hash,
  name,
  created_at,
  updated_at,
  email_verified_at
) values (
  '00000000-0000-0000-0000-000000000001',
  'e2e@example.com',
  '$2a$10$0d3vyAe1FWFUcTVfYV/qlOKSFddLFo4fxOR/sP9J7WIcR8tgAT8Y.',
  'E2E User',
  now(),
  now(),
  now()
);
