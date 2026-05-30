#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

echo "Formatting backend..."
(
  cd apps/core-api
  ./mvnw.cmd -q spotless:apply
)

echo "Formatting frontend..."
(
  cd apps/web
  pnpm.cmd lint --fix
)

echo "Formatting completed."
