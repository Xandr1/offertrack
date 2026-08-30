#!/usr/bin/env bash

set -euo pipefail
set +x
umask 077

PROJECT_ID="offertrack-staging"
REGION="europe-central2"
COMMIT_SHA=""
AI_URL=""
CORE_URL=""
WEB_URL=""
AI_IMAGE=""
CORE_IMAGE=""
WEB_IMAGE=""
AI_REVISION=""
CORE_REVISION=""
WEB_REVISION=""
CORE_DEPENDENCY_HEALTH_KEY="${CORE_DEPENDENCY_HEALTH_KEY:-}"

usage() {
  cat <<'EOF'
Usage: smoke.sh --commit SHA --ai-url URL --core-url URL --web-url URL \
  --ai-image DIGEST --core-image DIGEST --web-image DIGEST \
  --ai-revision NAME --core-revision NAME --web-revision NAME \
  [--project PROJECT] [--region REGION]

Runs bounded post-deployment checks without reading application secrets.
EOF
}

fail() {
  echo "staging-smoke: $*" >&2
  exit 1
}

require_value() {
  local option="$1"
  local value="${2:-}"
  [[ -n "$value" && "$value" != --* ]] || fail "$option requires a value"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) require_value "$1" "${2:-}"; PROJECT_ID="$2"; shift 2 ;;
    --region) require_value "$1" "${2:-}"; REGION="$2"; shift 2 ;;
    --commit) require_value "$1" "${2:-}"; COMMIT_SHA="$2"; shift 2 ;;
    --ai-url) require_value "$1" "${2:-}"; AI_URL="$2"; shift 2 ;;
    --core-url) require_value "$1" "${2:-}"; CORE_URL="$2"; shift 2 ;;
    --web-url) require_value "$1" "${2:-}"; WEB_URL="$2"; shift 2 ;;
    --ai-image) require_value "$1" "${2:-}"; AI_IMAGE="$2"; shift 2 ;;
    --core-image) require_value "$1" "${2:-}"; CORE_IMAGE="$2"; shift 2 ;;
    --web-image) require_value "$1" "${2:-}"; WEB_IMAGE="$2"; shift 2 ;;
    --ai-revision) require_value "$1" "${2:-}"; AI_REVISION="$2"; shift 2 ;;
    --core-revision) require_value "$1" "${2:-}"; CORE_REVISION="$2"; shift 2 ;;
    --web-revision) require_value "$1" "${2:-}"; WEB_REVISION="$2"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) fail "unknown argument: $1" ;;
  esac
done

[[ "$PROJECT_ID" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]] || fail "invalid project ID"
[[ "$REGION" =~ ^[a-z]+-[a-z]+[0-9]$ ]] || fail "invalid region"
[[ "$COMMIT_SHA" =~ ^[0-9a-f]{40}$ ]] || fail "commit must be a full lowercase Git SHA"

for url in "$AI_URL" "$CORE_URL" "$WEB_URL"; do
  [[ "$url" =~ ^https://[a-z0-9-]+-[0-9]+\.[a-z0-9-]+\.run\.app$ ]] \
    || fail "service URLs must be canonical HTTPS Cloud Run URLs"
done

for component_and_image in \
  "ai-service=$AI_IMAGE" \
  "core-api=$CORE_IMAGE" \
  "web=$WEB_IMAGE"; do
  component="${component_and_image%%=*}"
  image="${component_and_image#*=}"
  expected_prefix="${REGION}-docker.pkg.dev/${PROJECT_ID}/offertrack/${component}@sha256:"
  [[ "$image" == "${expected_prefix}"* ]] \
    && [[ "${image##*@sha256:}" =~ ^[0-9a-f]{64}$ ]] \
    || fail "$component image is not an exact staging digest"
done

for revision in "$AI_REVISION" "$CORE_REVISION" "$WEB_REVISION"; do
  [[ "$revision" =~ ^offertrack-stg-[a-z]+-g${COMMIT_SHA:0:12}-[0-9]+-[0-9]+$ ]] \
    || fail "revision does not identify the intended commit"
done

command -v curl >/dev/null 2>&1 || fail "curl is required"
command -v gcloud >/dev/null 2>&1 || fail "gcloud is required"
command -v jq >/dev/null 2>&1 || fail "jq is required"

RUNTIME_DIR="$(mktemp -d "${TMPDIR:-/tmp}/offertrack-staging-smoke.XXXXXX")"
cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  unset AI_ID_TOKEN
  unset CORE_DEPENDENCY_HEALTH_KEY
  case "$RUNTIME_DIR" in
    "${TMPDIR:-/tmp}"/offertrack-staging-smoke.*)
      rm -f -- "$RUNTIME_DIR"/*.json "$RUNTIME_DIR"/*.html
      rmdir -- "$RUNTIME_DIR" 2>/dev/null || true
      ;;
    *)
      echo "Refusing to clean an unexpected smoke directory." >&2
      [[ "$exit_code" -ne 0 ]] || exit_code=1
      ;;
  esac
  exit "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

retry_curl() {
  local destination="$1"
  local output_file="$2"
  shift 2
  curl \
    --fail \
    --silent \
    --show-error \
    --location \
    --connect-timeout 5 \
    --max-time 20 \
    --retry 4 \
    --retry-all-errors \
    --retry-delay 2 \
    --output "$output_file" \
    "$@" \
    "$destination"
}

AI_ID_TOKEN="$(gcloud auth print-identity-token --audiences "$AI_URL" --include-email)"
[[ -n "$AI_ID_TOKEN" ]] || fail "could not mint an AI service identity token"

retry_curl "$AI_URL/health" "$RUNTIME_DIR/ai.json" \
  --header "Authorization: Bearer $AI_ID_TOKEN"
jq -e '.status == "ok"' "$RUNTIME_DIR/ai.json" >/dev/null \
  || fail "AI health response was not healthy"
unset AI_ID_TOKEN

retry_curl "$CORE_URL/actuator/health/liveness" "$RUNTIME_DIR/core-liveness.json"
jq -e '.status == "UP"' "$RUNTIME_DIR/core-liveness.json" >/dev/null \
  || fail "Core liveness response was not UP"

retry_curl "$CORE_URL/actuator/health/readiness" "$RUNTIME_DIR/core-readiness.json"
jq -e '.status == "UP"' "$RUNTIME_DIR/core-readiness.json" >/dev/null \
  || fail "Core readiness response was not UP"

[[ -n "$CORE_DEPENDENCY_HEALTH_KEY" ]] || fail "dependency health key is required"

# This group is deliberately detail-free. An UP result proves that Core reached
# PostgreSQL and TLS Redis and authenticated to AI with both Cloud Run IAM and
# the internal API key.
retry_curl "$CORE_URL/actuator/health/dependencies" "$RUNTIME_DIR/core-dependencies.json" \
  --header "X-Internal-Api-Key: $CORE_DEPENDENCY_HEALTH_KEY"
jq -e '.status == "UP"' "$RUNTIME_DIR/core-dependencies.json" >/dev/null \
  || fail "Core dependency health response was not UP"

retry_curl "$WEB_URL/login" "$RUNTIME_DIR/web.html"
[[ -s "$RUNTIME_DIR/web.html" ]] || fail "Web returned an empty response"

check_service_revision() {
  local service_name="$1"
  local expected_revision="$2"
  local expected_image="$3"
  local ready_revision actual_image

  ready_revision="$(gcloud run services describe "$service_name" \
    --project "$PROJECT_ID" \
    --region "$REGION" \
    --format 'value(status.latestReadyRevisionName)')"
  [[ "$ready_revision" == "$expected_revision" ]] \
    || fail "$service_name is not serving the expected revision"

  actual_image="$(gcloud run revisions describe "$expected_revision" \
    --project "$PROJECT_ID" \
    --region "$REGION" \
    --format 'value(status.imageDigest)')"
  [[ "$actual_image" == "$expected_image" ]] \
    || fail "$service_name revision does not use the expected image digest"
}

check_service_revision offertrack-stg-ai "$AI_REVISION" "$AI_IMAGE"
check_service_revision offertrack-stg-core "$CORE_REVISION" "$CORE_IMAGE"
check_service_revision offertrack-stg-web "$WEB_REVISION" "$WEB_IMAGE"

job_image="$(gcloud run jobs describe offertrack-stg-migrate \
  --project "$PROJECT_ID" \
  --region "$REGION" \
  --format 'value(spec.template.spec.template.spec.containers[0].image)')"
[[ "$job_image" == "$CORE_IMAGE" ]] \
  || fail "migration job does not use the intended Core image digest"

echo "Staging smoke checks passed for commit $COMMIT_SHA."
