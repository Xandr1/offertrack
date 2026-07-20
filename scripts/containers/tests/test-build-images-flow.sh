#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
SOURCE_BUILD_SCRIPT="$REPO_ROOT/scripts/containers/build-images.sh"
TEST_PARENT="${TMPDIR:-/tmp}"
TEST_ROOT="$(mktemp -d "$TEST_PARENT/offertrack-container-flow.XXXXXX")"
FIXTURE_ROOT="$TEST_ROOT/repository"
FAKE_BIN="$TEST_ROOT/bin"
FLOW_RUNTIME_ROOT="$TEST_ROOT/runtime"
FLOW_DOCKER_LOG="$TEST_ROOT/docker.log"
FLOW_MAVEN_LOG="$TEST_ROOT/maven.log"
FLOW_JAR="$TEST_ROOT/core-api.jar"
REAL_BASH="${BASH:-/usr/bin/bash}"

cleanup() {
  local exit_code=$?
  trap - EXIT
  case "$TEST_ROOT" in
    "$TEST_PARENT"/offertrack-container-flow.*) rm -rf -- "$TEST_ROOT" ;;
    *)
      echo "Refusing to remove unexpected flow-test directory: $TEST_ROOT" >&2
      exit_code=1
      ;;
  esac
  exit "$exit_code"
}
trap cleanup EXIT

mkdir -p \
  "$FIXTURE_ROOT/scripts/containers" \
  "$FIXTURE_ROOT/apps/web/src/lib" \
  "$FIXTURE_ROOT/apps/core-api/target" \
  "$FIXTURE_ROOT/apps/ai-service" \
  "$FAKE_BIN" \
  "$FLOW_RUNTIME_ROOT"

cp -- "$SOURCE_BUILD_SCRIPT" "$FIXTURE_ROOT/scripts/containers/build-images.sh"
cp -- \
  "$REPO_ROOT/scripts/containers/validate-web-build-env.mjs" \
  "$FIXTURE_ROOT/scripts/containers/validate-web-build-env.mjs"
cp -- \
  "$REPO_ROOT/apps/web/src/lib/web-environment-validation.cjs" \
  "$FIXTURE_ROOT/apps/web/src/lib/web-environment-validation.cjs"
cp -- "$REPO_ROOT/apps/web/Dockerfile" "$FIXTURE_ROOT/apps/web/Dockerfile"
cp -- "$REPO_ROOT/apps/core-api/Dockerfile" "$FIXTURE_ROOT/apps/core-api/Dockerfile"
cp -- "$REPO_ROOT/apps/ai-service/Dockerfile" "$FIXTURE_ROOT/apps/ai-service/Dockerfile"
cp -- "$REPO_ROOT/compose.container-codegen.yml" "$FIXTURE_ROOT/compose.container-codegen.yml"
printf '%s' 'synthetic executable jar' >"$FLOW_JAR"

printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'printf '\''DATABASE_URL=%s\nDB_USER=%s\nDB_PASSWORD=%s\n'\'' "$DATABASE_URL" "$DB_USER" "$DB_PASSWORD" >"$FLOW_MAVEN_LOG"' \
  'printf '\''args'\'' >>"$FLOW_MAVEN_LOG"' \
  'for argument in "$@"; do printf '\''|%s'\'' "$argument" >>"$FLOW_MAVEN_LOG"; done' \
  'printf '\''\n'\'' >>"$FLOW_MAVEN_LOG"' \
  >"$FIXTURE_ROOT/apps/core-api/mvnw"
chmod +x "$FIXTURE_ROOT/apps/core-api/mvnw"

printf '%s\n' \
  '#!/usr/bin/env bash' \
  'printf '\''Linux\n'\''' \
  >"$FAKE_BIN/uname"

printf '%s\n' \
  '#!/usr/bin/env bash' \
  'if [[ "$*" == "-XshowSettings:properties -version" ]]; then' \
  '  printf '\''    java.specification.version = 21\n'\'' >&2' \
  '  exit 0' \
  'fi' \
  'exit 98' \
  >"$FAKE_BIN/java"

printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'case "${1:-}" in' \
  '  tf)' \
  '    printf '\''META-INF/MANIFEST.MF\n'\''' \
  '    if [[ "${FLOW_JAR_CONTENT_MODE:-complete}" != "missing-launcher" ]]; then' \
  '      printf '\''org/springframework/boot/loader/launch/JarLauncher.class\n'\''' \
  '    fi' \
  '    printf '\''BOOT-INF/classes/com/offertrack/CoreApiApplication.class\n'\''' \
  '    printf '\''BOOT-INF/lib/spring-boot.jar\n'\''' \
  '    ;;' \
  '  xf)' \
  '    mkdir -p META-INF' \
  '    printf '\''Main-Class: org.springframework.boot.loader.launch.JarLauncher\n'\'' >META-INF/MANIFEST.MF' \
  '    if [[ "${FLOW_JAR_CONTENT_MODE:-complete}" == "bad-start-class" ]]; then' \
  '      printf '\''Start-Class: com.example.WrongApplication\n'\'' >>META-INF/MANIFEST.MF' \
  '    else' \
  '      printf '\''Start-Class: com.offertrack.CoreApiApplication\n'\'' >>META-INF/MANIFEST.MF' \
  '    fi' \
  '    ;;' \
  '  *) exit 97 ;;' \
  'esac' \
  >"$FAKE_BIN/jar"

printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'case "${FLOW_FIND_MODE:-one}" in' \
  '  one) printf '\''%s\n'\'' "$FLOW_JAR" ;;' \
  '  none) ;;' \
  '  multiple) printf '\''%s\n%s\n'\'' "$FLOW_JAR" "${FLOW_JAR}.second" ;;' \
  '  *) exit 96 ;;' \
  'esac' \
  >"$FAKE_BIN/find"

printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'printf '\''command=%s\n'\'' "$*" >>"$FLOW_DOCKER_LOG"' \
  'if [[ "${1:-}" == "compose" && "$*" == *"--project-name"* ]]; then' \
  '  printf '\''project-compose-pg=%s|%s|%s\n'\'' "${POSTGRES_DB-<unset>}" "${POSTGRES_USER-<unset>}" "${POSTGRES_PASSWORD-<unset>}" >>"$FLOW_DOCKER_LOG"' \
  '  previous=""' \
  '  for argument in "$@"; do' \
  '    if [[ "$previous" == "--env-file" ]]; then' \
  '      env_contents="$(tr '\''\n'\'' '\'';'\'' <"$argument")"' \
  '      printf '\''env-file=%s\n'\'' "$env_contents" >>"$FLOW_DOCKER_LOG"' \
  '      break' \
  '    fi' \
  '    previous="$argument"' \
  '  done' \
  'fi' \
  'case "${1:-} ${2:-}" in' \
  '  "buildx version"|"compose version") exit 0 ;;' \
  'esac' \
  'if [[ "${1:-}" == "compose" ]]; then' \
  '  if [[ "$*" == *" port --index 1 codegen-postgres 5432"* ]]; then' \
  '    printf '\''%s\n'\'' "${FLOW_PORT_OUTPUT:-127.0.0.1:49152}"' \
  '  fi' \
  '  exit 0' \
  'fi' \
  'if [[ "${1:-} ${2:-}" == "buildx build" ]]; then exit 0; fi' \
  'if [[ "${1:-} ${2:-}" == "image inspect" ]]; then' \
  '  if [[ "$*" == *".Os"* ]]; then' \
  '    printf '\''linux/amd64\n'\''' \
  '  elif [[ "$*" == *".Config.Env"* ]]; then' \
  '    printf '\''NODE_ENV=production\n'\''' \
  '  fi' \
  '  exit 0' \
  'fi' \
  'exit 95' \
  >"$FAKE_BIN/docker"

chmod +x "$FAKE_BIN/uname" "$FAKE_BIN/java" "$FAKE_BIN/jar" "$FAKE_BIN/find" "$FAKE_BIN/docker"

run_flow_case() {
  local label="$1"
  local find_mode="$2"
  local jar_content_mode="$3"
  local port_output="$4"
  local expected_status="$5"
  local expected_message="$6"
  local output_file="$TEST_ROOT/${label}.out"

  rm -f -- "$FLOW_DOCKER_LOG" "$FLOW_MAVEN_LOG"
  local actual_status
  set +e
  PATH="$FAKE_BIN:$PATH" \
    TMPDIR="$FLOW_RUNTIME_ROOT" \
    POSTGRES_DB=hostile-caller-database \
    POSTGRES_USER=hostile-caller-user \
    POSTGRES_PASSWORD=hostile-caller-password \
    FLOW_DOCKER_LOG="$FLOW_DOCKER_LOG" \
    FLOW_MAVEN_LOG="$FLOW_MAVEN_LOG" \
    FLOW_JAR="$FLOW_JAR" \
    FLOW_FIND_MODE="$find_mode" \
    FLOW_JAR_CONTENT_MODE="$jar_content_mode" \
    FLOW_PORT_OUTPUT="$port_output" \
    OFFERTRACK_CODEGEN_PROJECT_NAME=offertrack-codegen-test-123-1 \
    "$REAL_BASH" "$FIXTURE_ROOT/scripts/containers/build-images.sh" \
      --app-env e2e \
      --next-public-api-url http://127.0.0.1:18081 \
      --tag flow-test \
      >"$output_file" 2>&1
  actual_status=$?
  set -e

  if [[ "$expected_status" == "zero" && "$actual_status" -ne 0 ]]; then
    echo "$label unexpectedly failed" >&2
    sed -n '1,80p' "$output_file" >&2
    return 1
  fi
  if [[ "$expected_status" == "nonzero" && "$actual_status" -eq 0 ]]; then
    echo "$label unexpectedly succeeded" >&2
    return 1
  fi
  if [[ -n "$expected_message" ]] && ! grep -Fq -- "$expected_message" "$output_file"; then
    echo "$label did not report: $expected_message" >&2
    sed -n '1,80p' "$output_file" >&2
    return 1
  fi
}

assert_codegen_cleanup_ran() {
  grep -Fq -- 'down --volumes --remove-orphans' "$FLOW_DOCKER_LOG"
  if compgen -G "$FLOW_RUNTIME_ROOT/offertrack-codegen.*" >/dev/null; then
    echo 'The build flow left its temporary codegen directory behind.' >&2
    return 1
  fi
}

run_flow_case success one complete 127.0.0.1:49152 zero ''
grep -Fxq 'DATABASE_URL=jdbc:postgresql://127.0.0.1:49152/offertrack_codegen' "$FLOW_MAVEN_LOG"
grep -Fxq 'DB_USER=offertrack_codegen' "$FLOW_MAVEN_LOG"
grep -Fxq 'DB_PASSWORD=container-codegen-postgres-password-not-for-production' "$FLOW_MAVEN_LOG"
grep -Fxq 'args|clean|flyway:migrate|package|-DskipTests' "$FLOW_MAVEN_LOG"
if grep -F 'project-compose-pg=' "$FLOW_DOCKER_LOG" | grep -Fvq 'project-compose-pg=<unset>|<unset>|<unset>'; then
  echo 'A caller POSTGRES_* value reached isolated codegen Compose.' >&2
  exit 1
fi
grep -Fq 'env-file=POSTGRES_DB=offertrack_codegen;POSTGRES_USER=offertrack_codegen;POSTGRES_PASSWORD=container-codegen-postgres-password-not-for-production;' "$FLOW_DOCKER_LOG"
[[ "$(grep -Fc 'buildx build --platform linux/amd64 --load' "$FLOW_DOCKER_LOG")" -eq 3 ]]
assert_codegen_cleanup_ran

run_flow_case invalid-port one complete 0.0.0.0:49152 nonzero 'unexpected codegen PostgreSQL port mapping'
[[ ! -e "$FLOW_MAVEN_LOG" ]]
assert_codegen_cleanup_ran

run_flow_case multiple-jars multiple complete 127.0.0.1:49152 nonzero 'expected exactly one executable Core API JAR, found 2'
[[ -e "$FLOW_MAVEN_LOG" ]]
assert_codegen_cleanup_ran

run_flow_case missing-launcher one missing-launcher 127.0.0.1:49152 nonzero 'Core API JAR is missing org/springframework/boot/loader/launch/JarLauncher.class'
assert_codegen_cleanup_ran

run_flow_case bad-start-class one bad-start-class 127.0.0.1:49152 nonzero 'Core API JAR has an unexpected Start-Class'
assert_codegen_cleanup_ran

echo 'Container build flow tests passed.'
