\set ON_ERROR_STOP on

\getenv app_password OFFERTRACK_DB_APP_PASSWORD
\getenv migrator_password OFFERTRACK_DB_MIGRATOR_PASSWORD

SELECT length(:'app_password') >= 32 AS app_password_valid \gset
SELECT length(:'migrator_password') >= 32 AS migrator_password_valid \gset

\if :app_password_valid
\else
  \echo 'The application database password must contain at least 32 characters.'
  \quit 3
\endif

\if :migrator_password_valid
\else
  \echo 'The migration database password must contain at least 32 characters.'
  \quit 3
\endif

BEGIN;

SELECT format('CREATE ROLE %I NOLOGIN', 'offertrack_app')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'offertrack_app')
\gexec

SELECT format('CREATE ROLE %I NOLOGIN', 'offertrack_migrator')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'offertrack_migrator')
\gexec

SELECT format(
  'ALTER ROLE %I WITH LOGIN PASSWORD %L NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS',
  'offertrack_app',
  :'app_password'
)
\gexec

SELECT format(
  'ALTER ROLE %I WITH LOGIN PASSWORD %L NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS',
  'offertrack_migrator',
  :'migrator_password'
)
\gexec

REVOKE ALL ON DATABASE offertrack FROM PUBLIC;
GRANT CONNECT ON DATABASE offertrack TO offertrack_app, offertrack_migrator;

-- Cloud SQL ownership transfers require temporary SET ROLE and database CREATE.
GRANT offertrack_migrator TO CURRENT_USER WITH INHERIT FALSE, SET TRUE;
GRANT CREATE ON DATABASE offertrack TO offertrack_migrator;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO offertrack_migrator;
GRANT USAGE ON SCHEMA public TO offertrack_app;

-- Normalize ownership if this procedure is adopted after an earlier manual
-- migration. Index ownership follows its table, so it does not need a separate
-- ALTER statement.
SELECT format('ALTER TABLE %I.%I OWNER TO %I', schemaname, tablename, 'offertrack_migrator')
FROM pg_catalog.pg_tables
WHERE schemaname = 'public'
  AND tableowner <> 'offertrack_migrator'
\gexec

SELECT format('ALTER SEQUENCE %I.%I OWNER TO %I', schemaname, sequencename, 'offertrack_migrator')
FROM pg_catalog.pg_sequences
WHERE schemaname = 'public'
  AND sequenceowner <> 'offertrack_migrator'
\gexec

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC, offertrack_app;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC, offertrack_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO offertrack_app;
GRANT SELECT, USAGE ON ALL SEQUENCES IN SCHEMA public TO offertrack_app;

ALTER DEFAULT PRIVILEGES FOR ROLE offertrack_migrator IN SCHEMA public
  REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE offertrack_migrator IN SCHEMA public
  REVOKE ALL ON SEQUENCES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE offertrack_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO offertrack_app;
ALTER DEFAULT PRIVILEGES FOR ROLE offertrack_migrator IN SCHEMA public
  GRANT SELECT, USAGE ON SEQUENCES TO offertrack_app;

REVOKE CREATE ON DATABASE offertrack FROM offertrack_migrator;
REVOKE offertrack_migrator FROM CURRENT_USER;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT FROM pg_catalog.pg_namespace
    WHERE nspname = 'public' AND nspowner = 'offertrack_migrator'::regrole
  ) THEN
    RAISE EXCEPTION 'The public schema must be owned by offertrack_migrator.';
  END IF;

  IF has_database_privilege('offertrack_migrator', 'offertrack', 'CREATE') THEN
    RAISE EXCEPTION 'The migration role must not retain database CREATE.';
  END IF;

  IF pg_has_role(CURRENT_USER, 'offertrack_migrator', 'SET') THEN
    RAISE EXCEPTION 'The bootstrap administrator must not retain SET ROLE to offertrack_migrator.';
  END IF;

  IF EXISTS (
    SELECT FROM pg_catalog.pg_roles
    WHERE rolname IN ('offertrack_app', 'offertrack_migrator')
      AND (rolsuper OR rolreplication OR rolcreatedb OR rolcreaterole
           OR rolbypassrls OR rolinherit OR NOT rolcanlogin)
  ) THEN
    RAISE EXCEPTION 'Database roles do not satisfy the required security attributes.';
  END IF;
END;
$$;

COMMIT;

\unset app_password
\unset migrator_password
