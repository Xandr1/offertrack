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
(
  cd apps/web
  pnpm.cmd lint
)

echo "Running frontend typecheck..."
(
  cd apps/web
  pnpm.cmd typecheck
)

echo "Running frontend unit tests..."
(
  cd apps/web
  pnpm.cmd test
)

echo "Running AI parser lint and tests..."
(
  cd apps/ai-parser
  python -m pip install -e ".[test]"
  python -m ruff check .
  python -m ruff format --check .
  python -m pytest
)

echo "All tests/checks passed."
