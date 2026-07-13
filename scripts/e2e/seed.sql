insert into users (
  id,
  email,
  password_hash,
  name,
  created_at,
  updated_at,
  email_verified_at
) values
  (
    '00000000-0000-0000-0000-000000000001',
    'navigation-user@e2e.invalid',
    '$2a$10$0d3vyAe1FWFUcTVfYV/qlOKSFddLFo4fxOR/sP9J7WIcR8tgAT8Y.',
    'Navigation Test User',
    now(),
    now(),
    now()
  ),
  (
    '00000000-0000-0000-0000-000000000002',
    'application-user@e2e.invalid',
    '$2a$10$0d3vyAe1FWFUcTVfYV/qlOKSFddLFo4fxOR/sP9J7WIcR8tgAT8Y.',
    'Application Test User',
    now(),
    now(),
    now()
  ),
  (
    '00000000-0000-0000-0000-000000000003',
    'applications-guard-user@e2e.invalid',
    '$2a$10$0d3vyAe1FWFUcTVfYV/qlOKSFddLFo4fxOR/sP9J7WIcR8tgAT8Y.',
    'Applications Guard Test User',
    now(),
    now(),
    now()
  ),
  (
    '00000000-0000-0000-0000-000000000004',
    'dashboard-guard-user@e2e.invalid',
    '$2a$10$0d3vyAe1FWFUcTVfYV/qlOKSFddLFo4fxOR/sP9J7WIcR8tgAT8Y.',
    'Dashboard Guard Test User',
    now(),
    now(),
    now()
  ),
  (
    '00000000-0000-0000-0000-000000000005',
    'settings-guard-user@e2e.invalid',
    '$2a$10$0d3vyAe1FWFUcTVfYV/qlOKSFddLFo4fxOR/sP9J7WIcR8tgAT8Y.',
    'Settings Guard Test User',
    now(),
    now(),
    now()
  ),
  (
    '00000000-0000-0000-0000-000000000006',
    'logout-user@e2e.invalid',
    '$2a$10$0d3vyAe1FWFUcTVfYV/qlOKSFddLFo4fxOR/sP9J7WIcR8tgAT8Y.',
    'Logout Test User',
    now(),
    now(),
    now()
  );
