#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

echo "Starting local infrastructure..."
docker compose up -d

echo "Starting backend and frontend..."

API_PID=""
WEB_PID=""

cleanup() {
  echo ""
  echo "Stopping dev processes..."
  if [ -n "$API_PID" ]; then
    kill "$API_PID" 2>/dev/null || true
  fi

  if [ -n "$WEB_PID" ]; then
    kill "$WEB_PID" 2>/dev/null || true
  fi
}

trap cleanup INT TERM EXIT

(
  cd apps/core-api
  ./mvnw.cmd spring-boot:run
) &
API_PID=$!

(
  pnpm --filter web run dev
) &
WEB_PID=$!

wait "$API_PID" "$WEB_PID"
