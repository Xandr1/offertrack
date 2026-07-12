#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
COMPOSE_FILE="$REPO_ROOT/docker-compose.e2e.yml"
COMPOSE_PROJECT_NAME="offertrack-e2e"
RUNTIME_ROOT="${TMPDIR:-/tmp}"
RUNTIME_DIR="$(mktemp -d "$RUNTIME_ROOT/offertrack-e2e.XXXXXX")"
API_PID=""
WEB_PID=""

set -a
. "$REPO_ROOT/.env.e2e.example"
set +a

compose() {
  docker compose --project-name "$COMPOSE_PROJECT_NAME" --file "$COMPOSE_FILE" "$@"
}

cleanup() {
  set +e

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
}

wait_for_url() {
  local name="$1"
  local url="$2"
  local timeout_seconds="${3:-90}"
  local elapsed=0

  until curl --fail --silent --show-error "$url" >/dev/null 2>&1; do
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

cd "$REPO_ROOT"

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
  "http://127.0.0.1:18080/actuator/health/readiness" 120

cd "$REPO_ROOT"
pnpm --dir apps/web run build
node "$REPO_ROOT/apps/web/node_modules/next/dist/bin/next" \
  start --hostname 127.0.0.1 --port 13000 \
  >"$RUNTIME_DIR/web.log" 2>&1 &
WEB_PID=$!

wait_for_url "Next.js" "http://127.0.0.1:13000/login" 90
pnpm --dir apps/web run e2e
