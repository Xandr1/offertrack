#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

IMAGE_NAME="${1:-offertrack-ai-service:runtime-verification}"
CONTAINER_ID=""

cleanup() {
  if [[ -n "$CONTAINER_ID" ]]; then
    docker rm --force "$CONTAINER_ID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

docker build --tag "$IMAGE_NAME" .
CONTAINER_ID="$(docker run --detach --read-only "$IMAGE_NAME")"

ready=false
for _ in {1..30}; do
  if ! docker inspect --format '{{.State.Running}}' "$CONTAINER_ID" | grep -qx true; then
    echo "The AI service runtime container exited before becoming ready." >&2
    docker logs "$CONTAINER_ID" >&2
    exit 1
  fi

  if docker exec "$CONTAINER_ID" python -c \
    "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2).read()" \
    >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done

if [[ "$ready" != true ]]; then
  echo "The AI service runtime container did not become healthy." >&2
  docker logs "$CONTAINER_ID" >&2
  exit 1
fi

pid_one_uids="$(docker exec "$CONTAINER_ID" awk '$1 == "Uid:" { print $2, $3 }' /proc/1/status)"
read -r runtime_real_uid runtime_effective_uid <<<"$pid_one_uids"
if [[ ! "$runtime_real_uid" =~ ^[0-9]+$ || ! "$runtime_effective_uid" =~ ^[0-9]+$ ]]; then
  echo "Could not determine the AI service PID 1 real and effective UIDs." >&2
  exit 1
fi
if [[ "$runtime_real_uid" == "0" || "$runtime_effective_uid" == "0" ]]; then
  echo "The AI service PID 1 process is running as root." >&2
  exit 1
fi

docker exec "$CONTAINER_ID" python -c \
  "import app.main, bs4, fastapi, httpx, openai, pydantic, uvicorn"

assert_module_absent() {
  local module_name="$1"
  docker exec "$CONTAINER_ID" python -c '
import importlib
import sys

module_name = sys.argv[1]
try:
    importlib.import_module(module_name)
except ModuleNotFoundError as exception:
    if exception.name != module_name:
        raise
else:
    raise SystemExit(f"{module_name} is installed in the runtime image")
' "$module_name"
}

docker exec "$CONTAINER_ID" python -c '
try:
    import pytest
except ModuleNotFoundError as exception:
    if exception.name != "pytest":
        raise
else:
    raise SystemExit("pytest is installed in the runtime image")
'
assert_module_absent ruff
assert_module_absent pyright

if docker exec "$CONTAINER_ID" sh -c 'command -v pytest || command -v ruff || command -v pyright'; then
  echo "A development or test command is present in the AI service runtime image." >&2
  exit 1
fi

echo "AI service runtime image verified with PID 1 real/effective UIDs " \
  "${runtime_real_uid}/${runtime_effective_uid} and runtime-only dependencies."
