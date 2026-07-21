#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/compose.container-smoke.yml"
BUILD_SCRIPT="$REPO_ROOT/scripts/containers/build-images.sh"
SEED_FILE="$REPO_ROOT/scripts/containers/seed-smoke.sql"
WEB_VERIFIER="$REPO_ROOT/scripts/containers/verify-web-output.py"
API_VERIFIER="$REPO_ROOT/scripts/containers/verify-smoke-api.py"
SANITIZER="$REPO_ROOT/scripts/e2e/sanitize-service-log.py"
SANITIZED_DIR="$REPO_ROOT/test-results/container-smoke"

NO_BUILD=false
IMAGE_TAG="local"
TAG_SEEN=false
SMOKE_TOUCHED=false
SMOKE_PROJECT=""
CODEGEN_PROJECT=""
RUNTIME_DIR=""
TEMP_PARENT=""
PYTHON_BIN=""

usage() {
  cat <<'EOF'
Usage: run-smoke.sh [--no-build] [--tag TAG]

Builds, starts, and verifies the production container images. --no-build uses
only exact application images already present in the local Docker image store.
EOF
}

fail() {
  echo "run-smoke.sh: $*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build)
      [[ "$NO_BUILD" == false ]] || fail "--no-build may only be specified once"
      NO_BUILD=true
      shift
      ;;
    --tag)
      [[ "$TAG_SEEN" == false ]] || fail "--tag may only be specified once"
      [[ -n "${2:-}" && "${2:-}" != --* ]] || fail "--tag requires a value"
      IMAGE_TAG="$2"
      TAG_SEEN=true
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *) fail "unknown argument: $1" ;;
  esac
done

if [[ ${#IMAGE_TAG} -gt 128 || ! "$IMAGE_TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]*$ ]]; then
  fail "--tag must be a valid Docker tag"
fi

if command -v python3 >/dev/null 2>&1 && python3 -c 'raise SystemExit(0)' >/dev/null 2>&1; then
  PYTHON_BIN=python3
elif command -v python >/dev/null 2>&1 && python -c 'raise SystemExit(0)' >/dev/null 2>&1; then
  PYTHON_BIN=python
else
  fail "Python 3 is required for bounded JSON and Web assertions"
fi

command -v docker >/dev/null 2>&1 || fail "Docker is required"
command -v curl >/dev/null 2>&1 || fail "curl is required"
docker compose version >/dev/null

validate_project_name() {
  local value="$1"
  if [[ ${#value} -gt 63 || ! "$value" =~ ^[a-z0-9][a-z0-9_-]*$ ]]; then
    fail "invalid Compose project name: $value"
  fi
}

SMOKE_PROJECT="${OFFERTRACK_SMOKE_PROJECT_NAME:-offertrack-smoke-local-$$-${RANDOM}}"
CODEGEN_PROJECT="${OFFERTRACK_CODEGEN_PROJECT_NAME:-offertrack-codegen-local-$$-${RANDOM}}"
validate_project_name "$SMOKE_PROJECT"
validate_project_name "$CODEGEN_PROJECT"

validate_ci_scope() {
  [[ "${GITHUB_ACTIONS:-}" == "true" ]] || return 0
  [[ "${GITHUB_RUN_ID:-}" =~ ^[1-9][0-9]*$ ]] || fail "GITHUB_RUN_ID must be numeric"
  [[ "${GITHUB_RUN_ATTEMPT:-}" =~ ^[1-9][0-9]*$ ]] \
    || fail "GITHUB_RUN_ATTEMPT must be numeric"
  [[ "$SMOKE_PROJECT" == "offertrack-smoke-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}" ]] \
    || fail "smoke project name does not match the exact CI scope"
  [[ "$CODEGEN_PROJECT" == "offertrack-codegen-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}" ]] \
    || fail "codegen project name does not match the exact CI scope"
  [[ -n "${RUNNER_TEMP:-}" && -d "$RUNNER_TEMP" ]] || fail "RUNNER_TEMP must exist in CI"
  local runner_temp expected_parent requested_parent
  runner_temp="$(cd -- "$RUNNER_TEMP" && pwd -P)"
  expected_parent="$runner_temp/offertrack-container-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
  requested_parent="${OFFERTRACK_CONTAINER_TEMP_ROOT:-}"
  [[ -n "$requested_parent" ]] || fail "OFFERTRACK_CONTAINER_TEMP_ROOT is required in CI"
  [[ "$(basename -- "$requested_parent")" == "offertrack-container-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}" ]] \
    || fail "temporary root does not match the exact CI scope"
  [[ "$(cd -- "$(dirname -- "$requested_parent")" && pwd -P)/$(basename -- "$requested_parent")" == "$expected_parent" ]] \
    || fail "temporary root must resolve beneath RUNNER_TEMP"
}

validate_ci_scope

if [[ -n "${OFFERTRACK_CONTAINER_TEMP_ROOT:-}" ]]; then
  TEMP_PARENT="$OFFERTRACK_CONTAINER_TEMP_ROOT"
  mkdir -p -- "$TEMP_PARENT"
  TEMP_PARENT="$(cd -- "$TEMP_PARENT" && pwd -P)"
  [[ "$TEMP_PARENT" != "/" ]] || fail "temporary root may not be the filesystem root"
  if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
    EXPECTED_CI_TEMP="$(cd -- "$RUNNER_TEMP" && pwd -P)/offertrack-container-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
    [[ "$TEMP_PARENT" == "$EXPECTED_CI_TEMP" ]] \
      || fail "temporary root resolved outside the exact CI scope"
  fi
  RUNTIME_DIR="$(mktemp -d "$TEMP_PARENT/smoke.${SMOKE_PROJECT}.XXXXXX")"
else
  TEMP_PARENT="${TMPDIR:-/tmp}"
  RUNTIME_DIR="$(mktemp -d "$TEMP_PARENT/offertrack-smoke.XXXXXX")"
fi

case "$SANITIZED_DIR" in
  "$REPO_ROOT"/test-results/container-smoke) ;;
  *) fail "sanitized diagnostics path escaped the repository contract" ;;
esac

compose() {
  OFFERTRACK_IMAGE_TAG="$IMAGE_TAG" docker compose \
    --project-name "$SMOKE_PROJECT" \
    --file "$COMPOSE_FILE" \
    "$@"
}

sanitize_file() {
  local source_file="$1"
  local destination_file="$2"
  local fallback="$3"
  if [[ ! -f "$source_file" ]] || ! "$PYTHON_BIN" "$SANITIZER" "$source_file" "$destination_file"; then
    rm -f -- "$destination_file"
    printf '%s\n' "$fallback" >"$destination_file"
  fi
}

collect_failure_diagnostics() {
  mkdir -p -- "$SANITIZED_DIR"
  local service raw sanitized
  for service in postgres redis ai-service core-api web; do
    raw="$RUNTIME_DIR/${service}.raw.log"
    sanitized="$SANITIZED_DIR/${service}.log"
    if ! compose logs --no-color --tail 250 "$service" >"$raw" 2>/dev/null; then
      printf '%s\n' "Raw logs were unavailable for $service." >"$raw"
    fi
    sanitize_file "$raw" "$sanitized" "Sanitization failed; raw logs were not preserved."
  done

  local raw_status="$RUNTIME_DIR/compose-status.raw.txt"
  if ! compose ps --all \
    --format 'table {{.Service}}\t{{.State}}\t{{.Status}}' \
    >"$raw_status" 2>/dev/null; then
    printf '%s\n' 'Compose status was unavailable.' >"$raw_status"
  fi
  sanitize_file \
    "$raw_status" \
    "$SANITIZED_DIR/compose-status.txt" \
    'Sanitization failed; raw Compose status was not preserved.'
}

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  set +e

  if [[ "$exit_code" -ne 0 ]]; then
    collect_failure_diagnostics
  fi
  if [[ "$SMOKE_TOUCHED" == true ]]; then
    if ! compose down --volumes --remove-orphans >/dev/null 2>&1; then
      [[ "$exit_code" -ne 0 ]] || exit_code=1
    fi
  fi

  if [[ -n "$RUNTIME_DIR" && -d "$RUNTIME_DIR" ]]; then
    case "$RUNTIME_DIR" in
      "$TEMP_PARENT"/smoke."$SMOKE_PROJECT".*|"$TEMP_PARENT"/offertrack-smoke.*)
        if ! rm -rf -- "$RUNTIME_DIR"; then
          [[ "$exit_code" -ne 0 ]] || exit_code=1
        fi
        ;;
      *)
        echo "Refusing to remove unexpected smoke directory: $RUNTIME_DIR" >&2
        exit_code=1
        ;;
    esac
  fi
  exit "$exit_code"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

rm -rf -- "$SANITIZED_DIR"

verify_image() {
  local image_ref="$1"
  docker image inspect "$image_ref" >/dev/null 2>&1 || fail "required local image is missing: $image_ref"
  local platform
  platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$image_ref")"
  [[ "$platform" == "linux/amd64" ]] || fail "$image_ref has architecture $platform, expected linux/amd64"
}

if [[ "$NO_BUILD" == false ]]; then
  OFFERTRACK_CODEGEN_PROJECT_NAME="$CODEGEN_PROJECT" \
    OFFERTRACK_CONTAINER_TEMP_ROOT="${OFFERTRACK_CONTAINER_TEMP_ROOT:-}" \
    bash "$BUILD_SCRIPT" \
      --app-env e2e \
      --next-public-api-url http://127.0.0.1:18081 \
      --tag "$IMAGE_TAG"
fi

verify_image "offertrack/web:$IMAGE_TAG"
verify_image "offertrack/core-api:$IMAGE_TAG"
verify_image "offertrack/ai-service:$IMAGE_TAG"

compose config --quiet

SMOKE_TOUCHED=true
compose down --volumes --remove-orphans >/dev/null 2>&1 || true
compose up --detach

container_id() {
  compose ps --quiet "$1"
}

wait_for_health() {
  local service="$1"
  local deadline=$((SECONDS + 120))
  while (( SECONDS < deadline )); do
    local id status
    id="$(container_id "$service" 2>/dev/null || true)"
    if [[ -n "$id" ]]; then
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id" 2>/dev/null || true)"
      if [[ "$status" == "healthy" ]]; then
        return 0
      fi
      if [[ "$(docker inspect --format '{{.State.Status}}' "$id" 2>/dev/null || true)" == "exited" ]]; then
        fail "$service exited before becoming healthy"
      fi
    fi
    if (( SECONDS < deadline )); then
      local remaining=$((deadline - SECONDS))
      if (( remaining > 2 )); then
        remaining=2
      fi
      sleep "$remaining"
    fi
  done
  fail "$service did not become healthy within 120 seconds"
}

wait_for_url() {
  local name="$1"
  local url="$2"
  local deadline=$((SECONDS + 120))
  while (( SECONDS < deadline )); do
    local remaining=$((deadline - SECONDS))
    local request_timeout=5
    if (( remaining < request_timeout )); then
      request_timeout="$remaining"
    fi
    if curl --fail --silent --show-error --max-time "$request_timeout" "$url" >/dev/null 2>&1; then
      return 0
    fi
    if (( SECONDS < deadline )); then
      remaining=$((deadline - SECONDS))
      if (( remaining > 2 )); then
        remaining=2
      fi
      sleep "$remaining"
    fi
  done
  fail "$name did not become ready within 120 seconds"
}

wait_for_health postgres
wait_for_health redis
wait_for_health ai-service
wait_for_url "Core API readiness" "http://127.0.0.1:18081/actuator/health/readiness"
wait_for_health web

verify_ai_and_core_health() {
  "$PYTHON_BIN" - "$1" <<'PY'
import json
import sys
from urllib.request import urlopen

if sys.argv[1] == "initial":
    with urlopen("http://127.0.0.1:18001/health", timeout=5) as response:
        assert response.status == 200
        assert json.load(response) == {"status": "ok"}

with urlopen("http://127.0.0.1:18081/actuator/health/readiness", timeout=5) as response:
    readiness = json.load(response)
assert readiness["status"] == "UP"
assert readiness["components"]["db"]["status"] == "UP"
assert readiness["components"]["redis"]["status"] == "UP"
PY
}

verify_ai_and_core_health initial

"$PYTHON_BIN" "$WEB_VERIFIER" \
  --base-url http://127.0.0.1:13001 \
  --expected-api-url http://127.0.0.1:18081

migration_summary="$(
  compose exec -T postgres psql \
    --username offertrack_container_smoke \
    --dbname offertrack_container_smoke \
    --tuples-only --no-align \
    --command 'select count(*) filter (where success), count(*) filter (where not success) from flyway_schema_history;' \
    | tr -d '\r[:space:]'
)"
if [[ ! "$migration_summary" =~ ^([0-9]+)\|0$ ]]; then
  fail "Flyway history did not contain successful migrations with zero failures"
fi
if (( BASH_REMATCH[1] < 1 )); then
  fail "Flyway history did not contain successful migrations with zero failures"
fi

compose exec -T postgres psql \
  --username offertrack_container_smoke \
  --dbname offertrack_container_smoke \
  --set ON_ERROR_STOP=1 \
  <"$SEED_FILE" >/dev/null

COOKIE_JAR="$RUNTIME_DIR/cookies.txt"
APPLICATION_ID_FILE="$RUNTIME_DIR/application-id.txt"
"$PYTHON_BIN" "$API_VERIFIER" before-restart \
  --base-url http://127.0.0.1:18081 \
  --cookie-jar "$COOKIE_JAR" \
  --application-id-file "$APPLICATION_ID_FILE"

assert_image_user() {
  local image_ref="$1"
  local expected="$2"
  local actual
  actual="$(docker image inspect --format '{{.Config.User}}' "$image_ref")"
  [[ "$actual" == "$expected" ]] || fail "$image_ref uses Config.User '$actual', expected '$expected'"
}

assert_container_user() {
  local service="$1"
  local expected="$2"
  local actual
  actual="$(docker inspect --format '{{.Config.User}}' "$(container_id "$service")")"
  [[ "$actual" == "$expected" ]] \
    || fail "$service container uses Config.User '$actual', expected '$expected'"
}

assert_pid_uid() {
  local service="$1"
  local expected="$2"
  local id uids
  id="$(container_id "$service")"
  uids="$(docker exec "$id" cat /proc/1/status | awk '$1 == "Uid:" { print $2, $3 }')"
  [[ "$uids" == "$expected $expected" ]] || fail "$service PID 1 UID is '$uids', expected '$expected $expected'"
}

assert_image_user "offertrack/web:$IMAGE_TAG" "1000:1000"
assert_image_user "offertrack/core-api:$IMAGE_TAG" "10001:10001"
assert_image_user "offertrack/ai-service:$IMAGE_TAG" "10001:10001"
assert_container_user web "1000:1000"
assert_container_user core-api "10001:10001"
assert_container_user ai-service "10001:10001"
assert_pid_uid web 1000
assert_pid_uid core-api 10001
assert_pid_uid ai-service 10001

assert_pid_command() {
  local service="$1"
  local executable="$2"
  shift 2
  local id actual_executable command expected
  id="$(container_id "$service")"
  actual_executable="$(basename "$(docker exec "$id" readlink /proc/1/exe)")"
  [[ "$actual_executable" == "$executable" ]] \
    || fail "$service PID 1 executable is '$actual_executable', expected '$executable'"
  command="$(docker exec "$id" sh -c "tr '\\000' ' ' < /proc/1/cmdline")"
  for expected in "$@"; do
    [[ " $command " == *" $expected "* ]] \
      || fail "$service PID 1 command is missing $expected"
  done
}

assert_pid_command web node
WEB_IMAGE_COMMAND="$(
  docker image inspect --format '{{json .Config.Cmd}}' "offertrack/web:$IMAGE_TAG"
)"
WEB_CONTAINER_COMMAND="$(
  docker inspect --format '{{json .Config.Cmd}}' "$(container_id web)"
)"
[[ "$WEB_IMAGE_COMMAND" == '["node","server.js"]' ]] \
  || fail "Web image command is '$WEB_IMAGE_COMMAND', expected node server.js"
[[ "$WEB_CONTAINER_COMMAND" == '["node","server.js"]' ]] \
  || fail "Web container command is '$WEB_CONTAINER_COMMAND', expected node server.js"
assert_pid_command core-api java -jar /app/app.jar

if docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "offertrack/web:$IMAGE_TAG" \
  | grep -Eq '^(APP_ENV|NEXT_PUBLIC_API_URL)='; then
  fail "Web final image environment contains a build-only variable"
fi
docker exec "$(container_id web)" node -e '
const values = require("node:fs").readFileSync("/proc/1/environ", "utf8").split("\0");
if (values.some(value => value.startsWith("APP_ENV=") || value.startsWith("NEXT_PUBLIC_API_URL="))) {
  process.exit(1);
}
' || fail "Web PID 1 environment contains a build-only variable"

docker exec "$(container_id web)" node -e '
const fs = require("node:fs");
for (const path of [
  "/app/apps/web/src",
  "/app/apps/web/e2e",
  "/app/apps/web/playwright.config.ts",
  "/app/apps/web/test-results",
  "/app/apps/web/.pnpm-store",
]) {
  if (fs.existsSync(path)) throw new Error(`forbidden Web artifact: ${path}`);
}
for (const root of ["/app", "/app/apps/web"]) {
  for (const name of fs.readdirSync(root)) {
    if (name === ".git" || name === ".pnpm-store" || name.startsWith(".env")) {
      throw new Error(`forbidden Web artifact: ${root}/${name}`);
    }
  }
}
for (const name of ["typescript", "eslint", "jest", "@playwright/test"]) {
  try { require.resolve(name); throw new Error(`development dependency installed: ${name}`); }
  catch (error) { if (!String(error).includes("Cannot find module")) throw error; }
}
'

docker exec "$(container_id core-api)" sh -c '
for path in /app/pom.xml /app/src /app/target /app/.mvn /app/.m2 /app/mvnw /app/.git /app/.env /app/.env.local; do
  test ! -e "$path" || exit 1
done
if command -v mvn >/dev/null 2>&1 || command -v javac >/dev/null 2>&1; then
  exit 1
fi
' || fail "Core runtime contains repository/build tooling"

docker exec "$(container_id ai-service)" python -c '
import importlib.util
from pathlib import Path
for path in (
    "/app/tests",
    "/app/pyproject.toml",
    "/app/pylock.toml",
    "/app/pylock.test.toml",
    "/app/.git",
    "/app/.env",
    "/app/.env.local",
):
    assert not Path(path).exists(), path

application_root = Path("/app/app")
assert application_root.is_dir(), application_root
forbidden_directories = {
    "test", "tests", "__pycache__", ".pytest_cache", ".ruff_cache",
    ".mypy_cache", ".pyright", "htmlcov",
}
for path in application_root.rglob("*"):
    relative = path.relative_to(application_root)
    name = path.name.lower()
    credential_file = path.is_file() and (
        path.suffix.lower() in {".key", ".p12", ".pem", ".pfx"}
        or name.startswith(("id_rsa", "id_ed25519"))
        or (name.endswith(".json") and any(
            marker in name
            for marker in ("credentials", "service-account", "service_account")
        ))
    )
    test_or_cache = forbidden_directories.intersection(
        part.lower() for part in relative.parts
    ) or (path.is_file() and (
        name in {"test.py", "conftest.py"}
        or name.startswith(("test_", ".coverage"))
        or name.endswith("_test.py")
        or path.suffix.lower() in {".pyc", ".pyo"}
    ))
    assert not (name.startswith(".env") or credential_file or test_or_cache), relative
for module in ("pytest", "ruff", "pyright"):
    assert importlib.util.find_spec(module) is None, module
' || fail "AI runtime contains repository secrets, tests, caches, or test-only packages"

AI_COMMAND="$(docker exec "$(container_id ai-service)" sh -c "tr '\\000' ' ' < /proc/1/cmdline")"
for expected in uvicorn app.main:app --host 0.0.0.0 --port 18001; do
  [[ " $AI_COMMAND " == *" $expected "* ]] || fail "AI PID 1 command is missing $expected"
done
AI_EXECUTABLE="$(basename "$(docker exec "$(container_id ai-service)" readlink /proc/1/exe)")"
case "$AI_EXECUTABLE" in
  python|python3|python3.11|uvicorn) ;;
  *) fail "AI PID 1 executable is unexpected: $AI_EXECUTABLE" ;;
esac

stop_and_start() {
  local service="$1"
  local id exit_code oom_killed
  id="$(container_id "$service")"
  compose stop --timeout 15 "$service"
  exit_code="$(docker inspect --format '{{.State.ExitCode}}' "$id")"
  oom_killed="$(docker inspect --format '{{.State.OOMKilled}}' "$id")"
  [[ "$oom_killed" == "false" ]] \
    || fail "$service was OOM-killed during SIGTERM handling"
  case "$exit_code" in
    0|143) ;;
    *) fail "$service did not stop cleanly after SIGTERM (exit=$exit_code)" ;;
  esac
  compose start "$service"
}

stop_and_start core-api
wait_for_url "Core API readiness after restart" "http://127.0.0.1:18081/actuator/health/readiness"
verify_ai_and_core_health core-restart
"$PYTHON_BIN" "$API_VERIFIER" after-restart \
  --base-url http://127.0.0.1:18081 \
  --cookie-jar "$COOKIE_JAR" \
  --application-id-file "$APPLICATION_ID_FILE"

stop_and_start ai-service
wait_for_health ai-service

stop_and_start web
wait_for_health web
WEB_RESTART_STATUS="$(
  curl --silent --show-error --max-time 5 \
    --output /dev/null \
    --write-out '%{http_code}' \
    http://127.0.0.1:13001/login
)"
[[ "$WEB_RESTART_STATUS" == "200" ]] \
  || fail "Web /login returned HTTP $WEB_RESTART_STATUS after restart"

echo "Container smoke suite passed for linux/amd64 tag '$IMAGE_TAG'."
