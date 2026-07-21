#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
BUILD_SCRIPT="$REPO_ROOT/scripts/containers/build-images.sh"
SMOKE_SCRIPT="$REPO_ROOT/scripts/containers/run-smoke.sh"
VALIDATOR="$REPO_ROOT/scripts/containers/validate-web-build-env.mjs"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/offertrack-container-args.XXXXXX")"
FAKE_BIN="$TEST_ROOT/bin"
FAKE_DOCKER_LOG="$TEST_ROOT/docker.log"
FAKE_DOCKER_MUTATION_MARKER="$TEST_ROOT/docker-mutation"

cleanup() {
  local exit_code=$?
  trap - EXIT
  case "$TEST_ROOT" in
    "${TMPDIR:-/tmp}"/offertrack-container-args.*) rm -rf -- "$TEST_ROOT" ;;
    *)
      echo "Refusing to remove unexpected test directory: $TEST_ROOT" >&2
      exit_code=1
      ;;
  esac
  exit "$exit_code"
}
trap cleanup EXIT

mkdir -p -- "$FAKE_BIN"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'printf '\''%s\n'\'' "$*" >>"$FAKE_DOCKER_LOG"' \
  'case "$*" in' \
  '  "buildx version"|"compose version") exit 0 ;;' \
  '  *) : >"$FAKE_DOCKER_MUTATION_MARKER"; exit 97 ;;' \
  'esac' \
  >"$FAKE_BIN/docker"
chmod +x "$FAKE_BIN/docker"
printf '%s\n' \
  '#!/usr/bin/env bash' \
  'if [[ "${1:-}" == "-c" ]]; then exit 0; fi' \
  'exit 98' \
  >"$FAKE_BIN/python3"
chmod +x "$FAKE_BIN/python3"

reset_fake_docker() {
  rm -f -- "$FAKE_DOCKER_LOG" "$FAKE_DOCKER_MUTATION_MARKER"
}

expect_pre_docker_rejection() {
  local label="$1"
  local expected_message="$2"
  shift 2
  reset_fake_docker

  local output_file="$TEST_ROOT/${label}.out"
  local exit_code
  set +e
  PATH="$FAKE_BIN:$PATH" \
    FAKE_DOCKER_LOG="$FAKE_DOCKER_LOG" \
    FAKE_DOCKER_MUTATION_MARKER="$FAKE_DOCKER_MUTATION_MARKER" \
    bash "$BUILD_SCRIPT" "$@" >"$output_file" 2>&1
  exit_code=$?
  set -e

  if [[ "$exit_code" -eq 0 ]]; then
    echo "$label unexpectedly succeeded" >&2
    return 1
  fi
  if ! grep -Fq -- "$expected_message" "$output_file"; then
    echo "$label did not report the expected rejection: $expected_message" >&2
    sed -n '1,20p' "$output_file" >&2
    return 1
  fi
  if [[ -e "$FAKE_DOCKER_LOG" || -e "$FAKE_DOCKER_MUTATION_MARKER" ]]; then
    echo "$label consulted Docker before rejecting invalid build inputs" >&2
    return 1
  fi
}

expect_valid_until_project_guard() {
  local app_env="$1"
  local api_url="$2"
  local tag="${3:-local}"
  reset_fake_docker

  local output_file="$TEST_ROOT/valid-${app_env}-${tag}.out"
  local exit_code
  set +e
  PATH="$FAKE_BIN:$PATH" \
    FAKE_DOCKER_LOG="$FAKE_DOCKER_LOG" \
    FAKE_DOCKER_MUTATION_MARKER="$FAKE_DOCKER_MUTATION_MARKER" \
    OFFERTRACK_CODEGEN_PROJECT_NAME='INVALID-PROJECT' \
    bash "$BUILD_SCRIPT" \
      --app-env "$app_env" \
      --next-public-api-url "$api_url" \
      --tag "$tag" \
      >"$output_file" 2>&1
  exit_code=$?
  set -e

  if [[ "$exit_code" -ne 2 ]]; then
    echo "valid $app_env/$tag inputs did not reach the project-name guard" >&2
    sed -n '1,20p' "$output_file" >&2
    return 1
  fi
  grep -Fq 'invalid Compose project name: INVALID-PROJECT' "$output_file"
  [[ ! -e "$FAKE_DOCKER_MUTATION_MARKER" ]]
  [[ "$(sed -n '1p' "$FAKE_DOCKER_LOG")" == 'buildx version' ]]
  [[ "$(sed -n '2p' "$FAKE_DOCKER_LOG")" == 'compose version' ]]
  [[ "$(wc -l <"$FAKE_DOCKER_LOG" | tr -d '[:space:]')" == 2 ]]
}

expect_smoke_project_rejection() {
  local label="$1"
  local smoke_project="$2"
  local codegen_project="$3"
  local expected_message="$4"
  reset_fake_docker

  local output_file="$TEST_ROOT/${label}.out"
  local exit_code
  set +e
  PATH="$FAKE_BIN:$PATH" \
    FAKE_DOCKER_LOG="$FAKE_DOCKER_LOG" \
    FAKE_DOCKER_MUTATION_MARKER="$FAKE_DOCKER_MUTATION_MARKER" \
    OFFERTRACK_SMOKE_PROJECT_NAME="$smoke_project" \
    OFFERTRACK_CODEGEN_PROJECT_NAME="$codegen_project" \
    bash "$SMOKE_SCRIPT" --no-build >"$output_file" 2>&1
  exit_code=$?
  set -e

  if [[ "$exit_code" -eq 0 ]]; then
    echo "$label unexpectedly succeeded" >&2
    return 1
  fi
  grep -Fq "$expected_message" "$output_file"
  [[ ! -e "$FAKE_DOCKER_MUTATION_MARKER" ]]
  [[ "$(sed -n '1p' "$FAKE_DOCKER_LOG")" == 'compose version' ]]
  [[ "$(wc -l <"$FAKE_DOCKER_LOG" | tr -d '[:space:]')" == 1 ]]
}

expect_pre_docker_rejection \
  missing-app-env '--app-env is required' \
  --next-public-api-url http://127.0.0.1:18081
expect_pre_docker_rejection \
  missing-api-url '--next-public-api-url is required' \
  --app-env e2e
expect_pre_docker_rejection \
  app-env-without-value '--app-env requires a value' \
  --app-env --next-public-api-url http://127.0.0.1:18081
expect_pre_docker_rejection \
  api-url-without-value '--next-public-api-url requires a value' \
  --app-env e2e --next-public-api-url
expect_pre_docker_rejection \
  unknown-argument 'unknown argument: --unexpected' \
  --app-env e2e --next-public-api-url http://127.0.0.1:18081 --unexpected
expect_pre_docker_rejection \
  duplicate-app-env '--app-env may only be specified once' \
  --app-env e2e --app-env test --next-public-api-url http://127.0.0.1:18081
expect_pre_docker_rejection \
  duplicate-api-url '--next-public-api-url may only be specified once' \
  --app-env e2e \
  --next-public-api-url http://127.0.0.1:18081 \
  --next-public-api-url http://127.0.0.1:18082
expect_pre_docker_rejection \
  duplicate-tag '--tag may only be specified once' \
  --app-env e2e --next-public-api-url http://127.0.0.1:18081 --tag one --tag two

expect_pre_docker_rejection \
  unknown-app-env 'APP_ENV must be one of:' \
  --app-env preview --next-public-api-url https://api.example.com
expect_pre_docker_rejection \
  case-sensitive-app-env 'APP_ENV must be one of:' \
  --app-env E2E --next-public-api-url https://api.example.com
expect_pre_docker_rejection \
  relative-url 'NEXT_PUBLIC_API_URL must be an absolute HTTP(S) URL.' \
  --app-env e2e --next-public-api-url /api
expect_pre_docker_rejection \
  non-http-url 'NEXT_PUBLIC_API_URL must be an absolute HTTP(S) URL.' \
  --app-env e2e --next-public-api-url ftp://api.example.com
expect_pre_docker_rejection \
  url-username 'NEXT_PUBLIC_API_URL must not include credentials.' \
  --app-env e2e --next-public-api-url https://user@api.example.com
expect_pre_docker_rejection \
  url-password 'NEXT_PUBLIC_API_URL must not include credentials.' \
  --app-env e2e --next-public-api-url https://user:password@api.example.com
expect_pre_docker_rejection \
  url-empty-userinfo 'NEXT_PUBLIC_API_URL must not include credentials.' \
  --app-env e2e --next-public-api-url https://@api.example.com
expect_pre_docker_rejection \
  url-query 'NEXT_PUBLIC_API_URL must not include a query or fragment.' \
  --app-env e2e --next-public-api-url 'https://api.example.com/path?debug=true'
expect_pre_docker_rejection \
  url-fragment 'NEXT_PUBLIC_API_URL must not include a query or fragment.' \
  --app-env e2e --next-public-api-url 'https://api.example.com/path#fragment'
expect_pre_docker_rejection \
  url-empty-query 'NEXT_PUBLIC_API_URL must not include a query or fragment.' \
  --app-env e2e --next-public-api-url 'https://api.example.com/path?'
expect_pre_docker_rejection \
  url-empty-fragment 'NEXT_PUBLIC_API_URL must not include a query or fragment.' \
  --app-env e2e --next-public-api-url 'https://api.example.com/path#'

expect_pre_docker_rejection \
  staging-http 'must use HTTPS in protected environments' \
  --app-env staging --next-public-api-url http://api.example.com
expect_pre_docker_rejection \
  production-localhost 'must not use localhost or a loopback address' \
  --app-env production --next-public-api-url https://localhost
expect_pre_docker_rejection \
  production-localhost-subdomain 'must not use localhost or a loopback address' \
  --app-env production --next-public-api-url https://api.localhost
expect_pre_docker_rejection \
  production-loopback-v4 'must not use localhost or a loopback address' \
  --app-env production --next-public-api-url https://127.42.0.1
expect_pre_docker_rejection \
  production-unspecified-v4 'must not use localhost or a loopback address' \
  --app-env production --next-public-api-url https://0.0.0.0
expect_pre_docker_rejection \
  production-unspecified-v6 'must not use localhost or a loopback address' \
  --app-env production --next-public-api-url 'https://[::]'
expect_pre_docker_rejection \
  production-loopback-v6 'must not use localhost or a loopback address' \
  --app-env production --next-public-api-url 'https://[::1]'
expect_pre_docker_rejection \
  production-mapped-loopback-v6 'must not use localhost or a loopback address' \
  --app-env production --next-public-api-url 'https://[::ffff:127.0.0.1]'
expect_pre_docker_rejection \
  production-mapped-v6 'must not use localhost or a loopback address' \
  --app-env production --next-public-api-url 'https://[::ffff:192.0.2.1]'
expect_pre_docker_rejection \
  production-integer-loopback 'must not use localhost or a loopback address' \
  --app-env production --next-public-api-url https://2130706433
expect_pre_docker_rejection \
  production-hex-loopback 'must not use localhost or a loopback address' \
  --app-env production --next-public-api-url https://0x7f000001

expect_pre_docker_rejection \
  tag-leading-hyphen '--tag must be a valid Docker tag' \
  --app-env e2e --next-public-api-url http://127.0.0.1:18081 --tag -unsafe
expect_pre_docker_rejection \
  tag-with-slash '--tag must be a valid Docker tag' \
  --app-env e2e --next-public-api-url http://127.0.0.1:18081 --tag feature/unsafe
expect_pre_docker_rejection \
  tag-with-colon '--tag must be a valid Docker tag' \
  --app-env e2e --next-public-api-url http://127.0.0.1:18081 --tag offertrack:unsafe
LONG_TAG="$(printf 'a%.0s' {1..129})"
expect_pre_docker_rejection \
  tag-too-long '--tag must be a valid Docker tag' \
  --app-env e2e --next-public-api-url http://127.0.0.1:18081 --tag "$LONG_TAG"

for app_env in local development test e2e; do
  expect_valid_until_project_guard "$app_env" http://127.0.0.1:18081 release_2026.07-rc.1
done
for app_env in staging production; do
  expect_valid_until_project_guard "$app_env" https://api.example.com _release
done

expect_smoke_project_rejection \
  invalid-smoke-project \
  'Offertrack-Smoke-123-1' \
  'offertrack-codegen-123-1' \
  'invalid Compose project name: Offertrack-Smoke-123-1'
expect_smoke_project_rejection \
  invalid-codegen-project \
  'offertrack-smoke-123-1' \
  '../offertrack-codegen-123-1' \
  'invalid Compose project name: ../offertrack-codegen-123-1'

# Also exercise the shared validator as a CLI so its argument-count contract is fast.
if node "$VALIDATOR" e2e >/dev/null 2>&1; then
  echo 'The shared validator accepted a missing URL argument' >&2
  exit 1
fi

echo 'Container build argument tests passed.'
