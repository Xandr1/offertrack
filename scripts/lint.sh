#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

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
