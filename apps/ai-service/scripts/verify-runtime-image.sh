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

docker buildx build --platform linux/amd64 --load --tag "$IMAGE_NAME" .

image_platform="$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$IMAGE_NAME")"
if [[ "$image_platform" != "linux/amd64" ]]; then
  echo "Expected a linux/amd64 AI service image, found $image_platform." >&2
  exit 1
fi

image_user="$(docker image inspect --format '{{.Config.User}}' "$IMAGE_NAME")"
if [[ "$image_user" != "10001:10001" ]]; then
  echo "Expected AI service image user 10001:10001, found ${image_user:-<empty>}." >&2
  exit 1
fi

mapfile -t image_environment < <(
  docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$IMAGE_NAME"
)
if ! printf '%s\n' "${image_environment[@]}" | grep -qx 'HOST=0.0.0.0'; then
  echo "The AI service image does not default HOST to 0.0.0.0." >&2
  exit 1
fi
if ! printf '%s\n' "${image_environment[@]}" | grep -qx 'PORT=8000'; then
  echo "The AI service image does not default PORT to 8000." >&2
  exit 1
fi

CONTAINER_ID="$(
  docker run \
    --platform linux/amd64 \
    --detach \
    --read-only \
    --tmpfs /tmp:size=64m,mode=1777,uid=10001,gid=10001 \
    "$IMAGE_NAME"
)"

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
pid_one_gids="$(docker exec "$CONTAINER_ID" awk '$1 == "Gid:" { print $2, $3 }' /proc/1/status)"
read -r runtime_real_gid runtime_effective_gid <<<"$pid_one_gids"
if [[ ! "$runtime_real_uid" =~ ^[0-9]+$ \
  || ! "$runtime_effective_uid" =~ ^[0-9]+$ \
  || ! "$runtime_real_gid" =~ ^[0-9]+$ \
  || ! "$runtime_effective_gid" =~ ^[0-9]+$ ]]; then
  echo "Could not determine the AI service PID 1 real/effective UID and GID." >&2
  exit 1
fi
if [[ "$runtime_real_uid" != "10001" \
  || "$runtime_effective_uid" != "10001" \
  || "$runtime_real_gid" != "10001" \
  || "$runtime_effective_gid" != "10001" ]]; then
  echo "Expected AI service PID 1 UID/GID 10001, found UID " \
    "${runtime_real_uid}/${runtime_effective_uid} and GID " \
    "${runtime_real_gid}/${runtime_effective_gid}." >&2
  exit 1
fi

docker exec "$CONTAINER_ID" python -c \
  "import app.main, bs4, fastapi, httpx, openai, pydantic, uvicorn; \
assert __import__('sys').prefix == '/opt/venv'; \
assert str(__import__('pathlib').Path(uvicorn.__file__).resolve()).startswith('/opt/venv/')"

pid_one_executable="$(docker exec "$CONTAINER_ID" readlink /proc/1/exe)"
case "$(basename "$pid_one_executable")" in
  python | python3 | python3.11 | uvicorn) ;;
  *)
    echo "Expected AI service PID 1 to be Python or Uvicorn, found $pid_one_executable." >&2
    exit 1
    ;;
esac

pid_one_command="$(docker exec "$CONTAINER_ID" sh -c "tr '\\000' ' ' < /proc/1/cmdline")"
for expected_argument in uvicorn app.main:app --host 0.0.0.0 --port 8000; do
  if [[ " $pid_one_command " != *" $expected_argument "* ]]; then
    echo "The AI service PID 1 command is missing expected argument: $expected_argument" >&2
    exit 1
  fi
done

for forbidden_path in /app/tests /app/pyproject.toml /app/pylock.toml /app/pylock.test.toml; do
  if docker exec "$CONTAINER_ID" sh -c 'test -e "$1"' sh "$forbidden_path"; then
    echo "Repository-only artifact is present in the AI runtime image: $forbidden_path" >&2
    exit 1
  fi
done

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

echo "AI service linux/amd64 runtime image verified with PID 1 real/effective UID/GID " \
  "${runtime_real_uid}/${runtime_effective_uid}/${runtime_real_gid}/${runtime_effective_gid}, " \
  "/opt/venv production dependencies, and " \
  "runtime defaults HOST=0.0.0.0 and PORT=8000."
