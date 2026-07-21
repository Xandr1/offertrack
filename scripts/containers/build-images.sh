#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
CODEGEN_COMPOSE_FILE="$REPO_ROOT/compose.container-codegen.yml"
CORE_DOCKERFILE="$REPO_ROOT/apps/core-api/Dockerfile"
WEB_DOCKERFILE="$REPO_ROOT/apps/web/Dockerfile"
AI_DOCKERFILE="$REPO_ROOT/apps/ai-service/Dockerfile"
ENV_VALIDATOR="$REPO_ROOT/scripts/containers/validate-web-build-env.mjs"

APP_ENV_VALUE=""
NEXT_PUBLIC_API_URL_VALUE=""
IMAGE_TAG="local"
APP_ENV_SEEN=false
API_URL_SEEN=false
TAG_SEEN=false
CODEGEN_STARTED=false
CODEGEN_PROJECT=""
CODEGEN_ENV_FILE=""
CODEGEN_RUNTIME_DIR=""
TEMP_PARENT=""

usage() {
  cat <<'EOF'
Usage: build-images.sh --app-env VALUE --next-public-api-url URL [--tag TAG]

Builds linux/amd64 production images and loads them into the local Docker image
store. APP_ENV and NEXT_PUBLIC_API_URL are public Web build inputs, not secrets.
EOF
}

fail() {
  echo "build-images.sh: $*" >&2
  exit 2
}

require_value() {
  local option="$1"
  local value="${2:-}"
  if [[ -z "$value" || "$value" == --* ]]; then
    fail "$option requires a value"
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --app-env)
      [[ "$APP_ENV_SEEN" == false ]] || fail "--app-env may only be specified once"
      require_value "$1" "${2:-}"
      APP_ENV_VALUE="$2"
      APP_ENV_SEEN=true
      shift 2
      ;;
    --next-public-api-url)
      [[ "$API_URL_SEEN" == false ]] || fail "--next-public-api-url may only be specified once"
      require_value "$1" "${2:-}"
      NEXT_PUBLIC_API_URL_VALUE="$2"
      API_URL_SEEN=true
      shift 2
      ;;
    --tag)
      [[ "$TAG_SEEN" == false ]] || fail "--tag may only be specified once"
      require_value "$1" "${2:-}"
      IMAGE_TAG="$2"
      TAG_SEEN=true
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      fail "unknown argument: $1"
      ;;
  esac
done

[[ "$APP_ENV_SEEN" == true ]] || fail "--app-env is required"
[[ "$API_URL_SEEN" == true ]] || fail "--next-public-api-url is required"

if [[ ${#IMAGE_TAG} -gt 128 || ! "$IMAGE_TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]*$ ]]; then
  fail "--tag must be a valid Docker tag"
fi

command -v node >/dev/null 2>&1 || fail "Node.js is required for Web build-input validation"
node "$ENV_VALIDATOR" "$APP_ENV_VALUE" "$NEXT_PUBLIC_API_URL_VALUE"

command -v java >/dev/null 2>&1 || fail "a Java 21 JDK is required to package Core API"
command -v jar >/dev/null 2>&1 || fail "the Java 21 JDK jar tool is required"
JAVA_SPEC_VERSION="$(
  java -XshowSettings:properties -version 2>&1 \
    | sed -n 's/^[[:space:]]*java\.specification\.version = //p' \
    | head -n 1 \
    | tr -d '\r'
)"
[[ "$JAVA_SPEC_VERSION" == "21" ]] || fail "Core API packaging requires Java 21"

# No Docker command is permitted above this point. Fast contract tests rely on
# invalid inputs failing before Docker can be consulted.
command -v docker >/dev/null 2>&1 || fail "Docker is required"
docker buildx version >/dev/null
docker compose version >/dev/null

validate_project_name() {
  local value="$1"
  if [[ ${#value} -gt 63 || ! "$value" =~ ^[a-z0-9][a-z0-9_-]*$ ]]; then
    fail "invalid Compose project name: $value"
  fi
}

CODEGEN_PROJECT="${OFFERTRACK_CODEGEN_PROJECT_NAME:-offertrack-codegen-local-$$-${RANDOM}}"
validate_project_name "$CODEGEN_PROJECT"

validate_ci_scope() {
  [[ "${GITHUB_ACTIONS:-}" == "true" ]] || return 0
  [[ "${GITHUB_RUN_ID:-}" =~ ^[1-9][0-9]*$ ]] || fail "GITHUB_RUN_ID must be numeric"
  [[ "${GITHUB_RUN_ATTEMPT:-}" =~ ^[1-9][0-9]*$ ]] \
    || fail "GITHUB_RUN_ATTEMPT must be numeric"
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
  CODEGEN_RUNTIME_DIR="$(mktemp -d "$TEMP_PARENT/codegen.${CODEGEN_PROJECT}.XXXXXX")"
else
  TEMP_PARENT="${TMPDIR:-/tmp}"
  CODEGEN_RUNTIME_DIR="$(mktemp -d "$TEMP_PARENT/offertrack-codegen.XXXXXX")"
fi

CODEGEN_ENV_FILE="$CODEGEN_RUNTIME_DIR/codegen.env"

codegen_compose() {
  env -u POSTGRES_DB -u POSTGRES_USER -u POSTGRES_PASSWORD docker compose \
    --project-name "$CODEGEN_PROJECT" \
    --project-directory "$CODEGEN_RUNTIME_DIR" \
    --env-file "$CODEGEN_ENV_FILE" \
    --file "$CODEGEN_COMPOSE_FILE" \
    "$@"
}

cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  set +e

  if [[ "$CODEGEN_STARTED" == true && -f "$CODEGEN_ENV_FILE" ]]; then
    if ! codegen_compose down --volumes --remove-orphans >/dev/null 2>&1; then
      [[ "$exit_code" -ne 0 ]] || exit_code=1
    fi
  fi

  if [[ -n "$CODEGEN_RUNTIME_DIR" && -d "$CODEGEN_RUNTIME_DIR" ]]; then
    case "$CODEGEN_RUNTIME_DIR" in
      "$TEMP_PARENT"/codegen."$CODEGEN_PROJECT".*|"$TEMP_PARENT"/offertrack-codegen.*)
        if ! rm -rf -- "$CODEGEN_RUNTIME_DIR"; then
          [[ "$exit_code" -ne 0 ]] || exit_code=1
        fi
        ;;
      *)
        echo "Refusing to remove unexpected codegen directory: $CODEGEN_RUNTIME_DIR" >&2
        exit_code=1
        ;;
    esac
  fi

  exit "$exit_code"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

printf '%s\n' \
  'POSTGRES_DB=offertrack_codegen' \
  'POSTGRES_USER=offertrack_codegen' \
  'POSTGRES_PASSWORD=container-codegen-postgres-password-not-for-production' \
  >"$CODEGEN_ENV_FILE"
chmod 600 "$CODEGEN_ENV_FILE" 2>/dev/null || true

echo "Starting isolated PostgreSQL for Flyway and lifecycle jOOQ generation..."
CODEGEN_STARTED=true
codegen_compose up --detach codegen-postgres

ready=false
for ((attempt = 1; attempt <= 30; attempt++)); do
  if codegen_compose exec -T codegen-postgres \
    pg_isready -U offertrack_codegen -d offertrack_codegen >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done
[[ "$ready" == true ]] || fail "codegen PostgreSQL did not become ready within 60 seconds"

published_address="$(codegen_compose port --index 1 codegen-postgres 5432 | tr -d '\r')"
if [[ ! "$published_address" =~ ^127\.0\.0\.1:([0-9]+)$ ]]; then
  fail "unexpected codegen PostgreSQL port mapping: $published_address"
fi
CODEGEN_PORT="${BASH_REMATCH[1]}"
if (( CODEGEN_PORT < 1 || CODEGEN_PORT > 65535 )); then
  fail "Docker returned an invalid codegen PostgreSQL port: $CODEGEN_PORT"
fi

MAVEN_COMMAND=("./mvnw")
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) MAVEN_COMMAND=("./mvnw.cmd") ;;
  *)
    if [[ ! -x "$REPO_ROOT/apps/core-api/mvnw" ]]; then
      MAVEN_COMMAND=(bash "./mvnw")
    fi
    ;;
esac

echo "Packaging Core API with Flyway followed by lifecycle jOOQ generation..."
(
  cd "$REPO_ROOT/apps/core-api"
  DATABASE_URL="jdbc:postgresql://127.0.0.1:${CODEGEN_PORT}/offertrack_codegen" \
    DB_USER="offertrack_codegen" \
    DB_PASSWORD="container-codegen-postgres-password-not-for-production" \
    "${MAVEN_COMMAND[@]}" clean flyway:migrate package -DskipTests
)

mapfile -t CORE_JARS < <(
  find "$REPO_ROOT/apps/core-api/target" -maxdepth 1 -type f -name '*.jar' \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*.original' -print
)
if [[ ${#CORE_JARS[@]} -ne 1 ]]; then
  fail "expected exactly one executable Core API JAR, found ${#CORE_JARS[@]}"
fi
CORE_JAR="${CORE_JARS[0]}"

JAR_LIST="$CODEGEN_RUNTIME_DIR/jar-contents.txt"
jar tf "$CORE_JAR" >"$JAR_LIST"
for required_entry in \
  'META-INF/MANIFEST.MF' \
  'org/springframework/boot/loader/launch/JarLauncher.class' \
  'BOOT-INF/classes/com/offertrack/CoreApiApplication.class'; do
  grep -Fxq "$required_entry" "$JAR_LIST" || fail "Core API JAR is missing $required_entry"
done
grep -q '^BOOT-INF/lib/.*\.jar$' "$JAR_LIST" || fail "Core API JAR has no BOOT-INF libraries"

MANIFEST_DIR="$CODEGEN_RUNTIME_DIR/manifest"
mkdir -p "$MANIFEST_DIR"
(
  cd "$MANIFEST_DIR"
  jar xf "$CORE_JAR" META-INF/MANIFEST.MF
)
tr -d '\r' <"$MANIFEST_DIR/META-INF/MANIFEST.MF" >"$CODEGEN_RUNTIME_DIR/manifest.txt"
grep -Fxq 'Main-Class: org.springframework.boot.loader.launch.JarLauncher' \
  "$CODEGEN_RUNTIME_DIR/manifest.txt" || fail "Core API JAR is not Spring Boot executable"
grep -Fxq 'Start-Class: com.offertrack.CoreApiApplication' \
  "$CODEGEN_RUNTIME_DIR/manifest.txt" || fail "Core API JAR has an unexpected Start-Class"

CORE_CONTEXT="$CODEGEN_RUNTIME_DIR/core-runtime-context"
mkdir -p "$CORE_CONTEXT"
cp -- "$CORE_JAR" "$CORE_CONTEXT/app.jar"

echo "Building linux/amd64 application images..."
docker buildx build --platform linux/amd64 --load \
  --file "$CORE_DOCKERFILE" \
  --tag "offertrack/core-api:$IMAGE_TAG" \
  "$CORE_CONTEXT"

docker buildx build --platform linux/amd64 --load \
  --file "$AI_DOCKERFILE" \
  --tag "offertrack/ai-service:$IMAGE_TAG" \
  "$REPO_ROOT/apps/ai-service"

docker buildx build --platform linux/amd64 --load \
  --file "$WEB_DOCKERFILE" \
  --build-arg "APP_ENV=$APP_ENV_VALUE" \
  --build-arg "NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL_VALUE" \
  --tag "offertrack/web:$IMAGE_TAG" \
  "$REPO_ROOT"

verify_image() {
  local image_ref="$1"
  local platform
  platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$image_ref")"
  [[ "$platform" == "linux/amd64" ]] || fail "$image_ref has architecture $platform, expected linux/amd64"
}

verify_image "offertrack/web:$IMAGE_TAG"
verify_image "offertrack/core-api:$IMAGE_TAG"
verify_image "offertrack/ai-service:$IMAGE_TAG"

if docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' \
  "offertrack/web:$IMAGE_TAG" | grep -Eq '^(APP_ENV|NEXT_PUBLIC_API_URL)='; then
  fail "Web final image environment contains a build-only variable"
fi

echo "Built and loaded linux/amd64 images with tag '$IMAGE_TAG'."
