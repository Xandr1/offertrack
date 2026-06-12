#!/usr/bin/env bash
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  . "$ROOT_DIR/.env"
  set +a
fi

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

POSTGRES_WAIT_TIMEOUT_SECONDS="${POSTGRES_WAIT_TIMEOUT_SECONDS:-60}"
POSTGRES_WAIT_INTERVAL_SECONDS=2
POSTGRES_ELAPSED_SECONDS=0
ENABLE_FLYWAY_INSECURE_FALLBACK="${ENABLE_FLYWAY_INSECURE_FALLBACK:-false}"

MAVEN_WRAPPER="./mvnw"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    MAVEN_WRAPPER="./mvnw.cmd"
    ;;
esac

echo "Starting postgres container..."
docker compose up -d postgres

echo "Waiting for postgres readiness..."
until docker compose exec -T postgres pg_isready -U offertrack -d offertrack >/dev/null 2>&1; do
  if [ "$POSTGRES_ELAPSED_SECONDS" -ge "$POSTGRES_WAIT_TIMEOUT_SECONDS" ]; then
    echo "Postgres was not ready within ${POSTGRES_WAIT_TIMEOUT_SECONDS}s."
    echo "Recent postgres logs:"
    docker compose logs postgres --tail=100 || true
    exit 1
  fi

  sleep "$POSTGRES_WAIT_INTERVAL_SECONDS"
  POSTGRES_ELAPSED_SECONDS=$((POSTGRES_ELAPSED_SECONDS + POSTGRES_WAIT_INTERVAL_SECONDS))
done

echo "Postgres is ready."

echo "Running Flyway migrations..."
(
  cd apps/core-api
  if "$MAVEN_WRAPPER" -q flyway:migrate; then
    exit 0
  fi

  if [ "$ENABLE_FLYWAY_INSECURE_FALLBACK" != "true" ]; then
    exit 1
  fi

  echo "Flyway migrate failed. Retrying with temporary Maven HTTPS insecure resolver mode..."
  "$MAVEN_WRAPPER" -q -Daether.connector.https.securityMode=insecure flyway:migrate
)

echo "Running jOOQ code generation..."
(
  cd apps/core-api
  "$MAVEN_WRAPPER" -q jooq-codegen:generate
)

echo "Backend codegen completed."
