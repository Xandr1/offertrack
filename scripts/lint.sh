#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

echo "Running frontend lint..."
pnpm --filter web run lint

echo "Running frontend typecheck..."
pnpm --filter web run typecheck

echo "Running backend formatting check..."
(
  cd apps/core-api
  ./mvnw.cmd -q spotless:check
)

echo "Running backend compile..."
(
  cd apps/core-api
  ./mvnw.cmd -q -DskipTests compile
)

echo "Code checks passed."
