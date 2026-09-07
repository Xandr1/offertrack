#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

echo "Starting local infrastructure..."
docker compose up -d

echo "Running backend tests..."
(
  cd apps/core-api
  ./mvnw.cmd test
)

echo "Running frontend lint..."
pnpm --filter web run lint

echo "Running frontend typecheck..."
pnpm --filter web run typecheck

echo "Running frontend unit tests..."
pnpm --filter web run test

echo "Running AI service lint and tests..."
(
  cd apps/ai-service
  python -m pip install --upgrade "pip==26.1.2"
  if uname -s | grep -qiE '^(MINGW|MSYS|CYGWIN)'; then
    python -m pip install --editable ".[test]"
  else
    sha256sum --check pylock.test.toml.sha256
    python -m pip install --requirement pylock.test.toml
  fi
  python -m ruff check .
  python -m ruff format --check .
  python -m pyright
  python -m pytest
)

echo "All tests/checks passed."
