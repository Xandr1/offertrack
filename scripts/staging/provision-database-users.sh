#!/usr/bin/env bash

set -euo pipefail
set +x
umask 077

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
SQL_FILE="$REPO_ROOT/scripts/staging/database/provision-users.sql"

PROJECT_ID="offertrack-staging"
DATABASE_HOST="127.0.0.1"
DATABASE_PORT="5432"
DATABASE_NAME="offertrack"
ADMIN_USER="postgres"
APP_PASSWORD_VERSION=""
MIGRATOR_PASSWORD_VERSION=""

usage() {
  cat <<'EOF'
Usage: provision-database-users.sh \
  --app-password-version NUMBER \
  --migrator-password-version NUMBER \
  [--project PROJECT] [--database-host HOST] [--database-port PORT] \
  [--database-name NAME] [--admin-user USER]

Creates or reconciles the staging application and migration database roles.
Run it from an authorized private Cloud SQL path, normally through a Cloud SQL
Auth Proxy listening on 127.0.0.1:5432. The bootstrap administrator password is
read by psql's secure prompt unless the operator has provided another supported
PostgreSQL authentication mechanism.
EOF
}

fail() {
  echo "provision-database-users.sh: $*" >&2
  exit 2
}

require_value() {
  local option="$1"
  local value="${2:-}"
  [[ -n "$value" && "$value" != --* ]] || fail "$option requires a value"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project)
      require_value "$1" "${2:-}"
      PROJECT_ID="$2"
      shift 2
      ;;
    --database-host)
      require_value "$1" "${2:-}"
      DATABASE_HOST="$2"
      shift 2
      ;;
    --database-port)
      require_value "$1" "${2:-}"
      DATABASE_PORT="$2"
      shift 2
      ;;
    --database-name)
      require_value "$1" "${2:-}"
      DATABASE_NAME="$2"
      shift 2
      ;;
    --admin-user)
      require_value "$1" "${2:-}"
      ADMIN_USER="$2"
      shift 2
      ;;
    --app-password-version)
      require_value "$1" "${2:-}"
      APP_PASSWORD_VERSION="$2"
      shift 2
      ;;
    --migrator-password-version)
      require_value "$1" "${2:-}"
      MIGRATOR_PASSWORD_VERSION="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *) fail "unknown argument: $1" ;;
  esac
done

[[ "$PROJECT_ID" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]] || fail "invalid project ID"
[[ "$DATABASE_PORT" =~ ^[1-9][0-9]{0,4}$ ]] || fail "invalid database port"
(( DATABASE_PORT <= 65535 )) || fail "invalid database port"
[[ "$DATABASE_NAME" =~ ^[a-z_][a-z0-9_]{0,62}$ ]] || fail "invalid database name"
[[ "$ADMIN_USER" =~ ^[A-Za-z_][A-Za-z0-9_.@-]{0,127}$ ]] || fail "invalid admin user"
[[ "$APP_PASSWORD_VERSION" =~ ^[1-9][0-9]*$ ]] \
  || fail "--app-password-version must be a pinned numeric version"
[[ "$MIGRATOR_PASSWORD_VERSION" =~ ^[1-9][0-9]*$ ]] \
  || fail "--migrator-password-version must be a pinned numeric version"
[[ -f "$SQL_FILE" ]] || fail "database provisioning SQL is missing"

command -v gcloud >/dev/null 2>&1 || fail "gcloud is required"
command -v psql >/dev/null 2>&1 || fail "psql is required"

TEMP_PARENT="${TMPDIR:-/tmp}"
RUNTIME_DIR="$(mktemp -d "$TEMP_PARENT/offertrack-db-bootstrap.XXXXXX")"

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  unset OFFERTRACK_DB_APP_PASSWORD OFFERTRACK_DB_MIGRATOR_PASSWORD
  case "$RUNTIME_DIR" in
    "$TEMP_PARENT"/offertrack-db-bootstrap.*)
      rm -f -- "$RUNTIME_DIR/app-password" "$RUNTIME_DIR/migrator-password"
      rmdir -- "$RUNTIME_DIR" 2>/dev/null || true
      ;;
    *)
      echo "Refusing to clean an unexpected bootstrap directory." >&2
      [[ "$exit_code" -ne 0 ]] || exit_code=1
      ;;
  esac
  exit "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

gcloud secrets versions access "$APP_PASSWORD_VERSION" \
  --project "$PROJECT_ID" \
  --secret offertrack-stg-db-app-password \
  --out-file "$RUNTIME_DIR/app-password" \
  --quiet >/dev/null
gcloud secrets versions access "$MIGRATOR_PASSWORD_VERSION" \
  --project "$PROJECT_ID" \
  --secret offertrack-stg-db-migrator-password \
  --out-file "$RUNTIME_DIR/migrator-password" \
  --quiet >/dev/null

OFFERTRACK_DB_APP_PASSWORD="$(<"$RUNTIME_DIR/app-password")"
OFFERTRACK_DB_MIGRATOR_PASSWORD="$(<"$RUNTIME_DIR/migrator-password")"
export OFFERTRACK_DB_APP_PASSWORD OFFERTRACK_DB_MIGRATOR_PASSWORD

[[ ${#OFFERTRACK_DB_APP_PASSWORD} -ge 32 ]] || fail "application password version is unusable"
[[ ${#OFFERTRACK_DB_MIGRATOR_PASSWORD} -ge 32 ]] || fail "migration password version is unusable"
[[ "$OFFERTRACK_DB_APP_PASSWORD" != "$OFFERTRACK_DB_MIGRATOR_PASSWORD" ]] \
  || fail "application and migration password versions must contain different values"

echo "Reconciling the staging database roles and grants..."
PGAPPNAME=offertrack-db-bootstrap \
  psql \
    --no-psqlrc \
    --set ON_ERROR_STOP=1 \
    --host "$DATABASE_HOST" \
    --port "$DATABASE_PORT" \
    --dbname "$DATABASE_NAME" \
    --username "$ADMIN_USER" \
    --file "$SQL_FILE"

echo "Database roles and grants were reconciled successfully."
