#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
COMPOSE_FILE="$REPO_ROOT/docker-compose.e2e.yml"
COMPOSE_PROJECT_NAME="offertrack-e2e"
WEB_ROOT="$REPO_ROOT/apps/web"
SERVICE_LOG_DIR="$WEB_ROOT/test-results/service-logs"
LOG_SANITIZER="$REPO_ROOT/scripts/e2e/sanitize-service-log.py"
RUNTIME_ROOT="${TMPDIR:-/tmp}"
RUNTIME_DIR="$(mktemp -d "$RUNTIME_ROOT/offertrack-e2e.XXXXXX")"
API_PID=""
WEB_PID=""
PYTHON_BIN=""

if command -v python3 >/dev/null 2>&1 &&
  python3 -c 'raise SystemExit(0)' >/dev/null 2>&1; then
  PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1 &&
  python -c 'raise SystemExit(0)' >/dev/null 2>&1; then
  PYTHON_BIN="python"
fi

compose() {
  docker compose --project-name "$COMPOSE_PROJECT_NAME" --file "$COMPOSE_FILE" "$@"
}

sanitize_service_log() {
  local source_file="$1"
  local destination_file="$2"
  local unavailable_message="$3"

  if [[ ! -f "$source_file" ]]; then
    printf '%s\n' "$unavailable_message" >"$destination_file"
    return
  fi

  if [[ -z "$PYTHON_BIN" ]] ||
    ! "$PYTHON_BIN" "$LOG_SANITIZER" "$source_file" "$destination_file"; then
    rm -f -- "$destination_file"
    printf '%s\n' "Log sanitization failed; the raw log was not preserved." \
      >"$destination_file"
  fi
}

collect_failure_diagnostics() {
  mkdir -p "$SERVICE_LOG_DIR"

  local api_log="$SERVICE_LOG_DIR/core-api.log"
  local web_log="$SERVICE_LOG_DIR/web.log"
  local compose_status="$SERVICE_LOG_DIR/compose-status.txt"
  local raw_compose_status="$RUNTIME_DIR/compose-status.txt"

  sanitize_service_log \
    "$RUNTIME_DIR/core-api.log" \
    "$api_log" \
    "Core API log was not created."
  sanitize_service_log \
    "$RUNTIME_DIR/web.log" \
    "$web_log" \
    "Next.js log was not created."

  if compose ps --all \
    --format 'table {{.Service}}\t{{.State}}\t{{.Status}}' \
    >"$raw_compose_status" 2>/dev/null; then
    sanitize_service_log \
      "$raw_compose_status" \
      "$compose_status" \
      "Docker Compose status was not available."
  else
    printf '%s\n' "Docker Compose status was not available." >"$compose_status"
  fi

  echo "Core API log (last 250 sanitized lines):"
  tail -n 250 "$api_log"
  echo "Next.js log (last 250 sanitized lines):"
  tail -n 250 "$web_log"
  echo "Docker Compose service status (address fields excluded):"
  cat "$compose_status"
}

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  set +e

  if [[ "$exit_code" -ne 0 ]]; then
    collect_failure_diagnostics
  fi

  if [[ -n "$WEB_PID" ]]; then
    kill "$WEB_PID" >/dev/null 2>&1 || true
    wait "$WEB_PID" >/dev/null 2>&1 || true
  fi

  if [[ -n "$API_PID" ]]; then
    kill "$API_PID" >/dev/null 2>&1 || true
    wait "$API_PID" >/dev/null 2>&1 || true
  fi

  compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  if [[ "$RUNTIME_DIR" == "$RUNTIME_ROOT"/offertrack-e2e.* && -d "$RUNTIME_DIR" ]]; then
    rm -rf -- "$RUNTIME_DIR"
  fi

  exit "$exit_code"
}

wait_for_url() {
  local name="$1"
  local url="$2"
  local process_pid="$3"
  local timeout_seconds="${4:-90}"
  local elapsed=0

  until curl --fail --silent --show-error "$url" >/dev/null 2>&1; do
    if ! kill -0 "$process_pid" >/dev/null 2>&1; then
      echo "$name process exited before becoming ready."
      return 1
    fi

    if [[ "$elapsed" -ge "$timeout_seconds" ]]; then
      echo "$name did not become ready within ${timeout_seconds}s."
      return 1
    fi

    sleep 1
    elapsed=$((elapsed + 1))
  done
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

set -a
. "$REPO_ROOT/.env.e2e.example"
set +a

cd "$REPO_ROOT"

# Avoid retaining artifacts from an earlier local run if startup fails before
# Playwright has a chance to clear its own output directory.
if [[ "$WEB_ROOT" != "$REPO_ROOT/apps/web" ]] ||
  [[ "$SERVICE_LOG_DIR" != "$REPO_ROOT/apps/web/test-results/service-logs" ]]; then
  echo "Refusing to clear E2E artifacts outside the expected web workspace."
  exit 1
fi
rm -rf -- "$WEB_ROOT/test-results" "$WEB_ROOT/playwright-report"

# A fresh isolated volume is the primary database/reset mechanism.
compose down --volumes --remove-orphans >/dev/null 2>&1 || true
compose up --detach postgres redis mailpit

export BACKEND_CODEGEN_COMPOSE_FILE="$COMPOSE_FILE"
export BACKEND_CODEGEN_COMPOSE_PROJECT_NAME="$COMPOSE_PROJECT_NAME"
export BACKEND_CODEGEN_POSTGRES_SERVICE="postgres"
export BACKEND_CODEGEN_ENV_FILE="$REPO_ROOT/.env.e2e.example"
./scripts/backend-codegen.sh

cd "$REPO_ROOT/apps/core-api"
if [[ "$(uname -s)" =~ ^(MINGW|MSYS|CYGWIN) ]]; then
  ./mvnw.cmd -DskipTests package
else
  ./mvnw -DskipTests package
fi

compose exec -T postgres \
  psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set ON_ERROR_STOP=1 < "$REPO_ROOT/scripts/e2e/seed.sql"

java -jar "$REPO_ROOT/apps/core-api/target/core-api-0.0.1-SNAPSHOT.jar" \
  >"$RUNTIME_DIR/core-api.log" 2>&1 &
API_PID=$!

wait_for_url "Core API readiness" \
  "http://127.0.0.1:18080/actuator/health/readiness" "$API_PID" 120

cd "$REPO_ROOT"
pnpm --dir "$WEB_ROOT" run build
(
  cd "$WEB_ROOT"
  exec node "$WEB_ROOT/node_modules/next/dist/bin/next" \
    start --hostname 127.0.0.1 --port 13000
) >"$RUNTIME_DIR/web.log" 2>&1 &
WEB_PID=$!

wait_for_url "Next.js" "http://127.0.0.1:13000/login" "$WEB_PID" 90
pnpm --dir "$WEB_ROOT" run e2e
